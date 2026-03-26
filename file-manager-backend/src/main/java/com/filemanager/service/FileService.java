package com.filemanager.service;

import com.filemanager.config.PasswordEncryptor;
import com.filemanager.dto.FileDTO;
import com.filemanager.entity.FileMetadata;
import com.filemanager.entity.ServerConfig;
import com.filemanager.repository.FileMetadataRepository;
import com.filemanager.repository.ServerConfigRepository;
import com.github.sardine.DavResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FileService {
    
    @Autowired
    private ServerConfigRepository serverConfigRepository;
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    
    @Autowired
    private WebDavService webDavService;
    
    @Autowired
    private PasswordEncryptor passwordEncryptor;
    
    private static final Map<String, Map<String, String>> FILE_TYPE_MAP = new HashMap<>();
    
    static {
        FILE_TYPE_MAP.put("pdf", Map.of("icon", "pdf", "color", "#E74C3C", "type", "document"));
        FILE_TYPE_MAP.put("doc", Map.of("icon", "word", "color", "#3498DB", "type", "document"));
        FILE_TYPE_MAP.put("docx", Map.of("icon", "word", "color", "#3498DB", "type", "document"));
        FILE_TYPE_MAP.put("xls", Map.of("icon", "excel", "color", "#27AE60", "type", "document"));
        FILE_TYPE_MAP.put("xlsx", Map.of("icon", "excel", "color", "#27AE60", "type", "document"));
        FILE_TYPE_MAP.put("jpg", Map.of("icon", "image", "color", "#9B59B6", "type", "image"));
        FILE_TYPE_MAP.put("jpeg", Map.of("icon", "image", "color", "#9B59B6", "type", "image"));
        FILE_TYPE_MAP.put("png", Map.of("icon", "image", "color", "#9B59B6", "type", "image"));
        FILE_TYPE_MAP.put("gif", Map.of("icon", "image", "color", "#9B59B6", "type", "image"));
        FILE_TYPE_MAP.put("mp4", Map.of("icon", "video", "color", "#E91E63", "type", "video"));
        FILE_TYPE_MAP.put("avi", Map.of("icon", "video", "color", "#E91E63", "type", "video"));
        FILE_TYPE_MAP.put("mp3", Map.of("icon", "audio", "color", "#00BCD4", "type", "audio"));
        FILE_TYPE_MAP.put("wav", Map.of("icon", "audio", "color", "#00BCD4", "type", "audio"));
        FILE_TYPE_MAP.put("zip", Map.of("icon", "archive", "color", "#FF9800", "type", "archive"));
        FILE_TYPE_MAP.put("txt", Map.of("icon", "text", "color", "#95A5A6", "type", "document"));
        FILE_TYPE_MAP.put("md", Map.of("icon", "markdown", "color", "#34495E", "type", "document"));
    }
    
    public List<FileDTO> getFileTree(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        List<DavResource> resources = webDavService.listFiles(server, path);
        
        return resources.stream()
                .filter(r -> !r.getPath().equals(path))
                .map(r -> toDTO(r, serverId))
                .collect(Collectors.toList());
    }
    
    public Page<FileDTO> getFileList(Long serverId, String path, String name, String type,
                                      String startDate, String endDate,
                                      String sortBy, String sortOrder,
                                      int page, int size) {
        Specification<FileMetadata> spec = Specification.where(null);
        
        if (serverId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("serverId"), serverId));
        }
        
        if (path != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("path"), path));
        }
        
        if (name != null && !name.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("name"), "%" + name + "%"));
        }
        
        if (type != null && !type.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("contentType"), type + "%"));
        }
        
        Sort sort = Sort.by("asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC,
                            sortBy != null ? sortBy : "name");
        Pageable pageable = PageRequest.of(page, size, sort);
        
        return fileMetadataRepository.findAll(spec, pageable)
                .map(this::toDTO);
    }
    
    @Transactional
    public void syncFiles(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        List<DavResource> resources = webDavService.listFiles(server, path);
        
        for (DavResource resource : resources) {
            if (resource.getPath().equals(path)) continue;
            
            FileMetadata metadata = new FileMetadata();
            metadata.setServerId(serverId);
            metadata.setPath(resource.getPath());
            metadata.setName(resource.getName());
            metadata.setIsDirectory(resource.isDirectory());
            metadata.setSize(resource.getContentLength());
            metadata.setContentType(resource.getContentType());
            
            if (resource.getModified() != null) {
                metadata.setLastModified(LocalDateTime.ofInstant(
                        resource.getModified().toInstant(), ZoneId.systemDefault()));
            }
            
            fileMetadataRepository.save(metadata);
        }
    }
    
    @Transactional
    public void uploadFile(Long serverId, String path, String filename, InputStream data) {
        ServerConfig server = getServer(serverId);
        String fullPath = path.endsWith("/") ? path + filename : path + "/" + filename;
        webDavService.uploadFile(server, fullPath, data);
    }
    
    public InputStream downloadFile(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        return webDavService.downloadFile(server, path);
    }
    
    @Transactional
    public void renameFile(Long serverId, String oldPath, String newPath) {
        ServerConfig server = getServer(serverId);
        webDavService.move(server, oldPath, newPath);
    }
    
    @Transactional
    public void deleteFile(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        webDavService.delete(server, path);
    }
    
    @Transactional
    public void createFolder(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        webDavService.createDirectory(server, path);
    }
    
    private ServerConfig getServer(Long serverId) {
        ServerConfig server = serverConfigRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("服务器不存在"));
        
        if (server.getPassword() != null) {
            server.setPassword(passwordEncryptor.decrypt(server.getPassword()));
        }
        
        return server;
    }
    
    private FileDTO toDTO(DavResource resource, Long serverId) {
        FileDTO dto = new FileDTO();
        dto.setServerId(serverId);
        dto.setPath(resource.getPath());
        dto.setName(resource.getName());
        dto.setIsDirectory(resource.isDirectory());
        dto.setSize(resource.getContentLength());
        dto.setContentType(resource.getContentType());
        
        if (resource.getModified() != null) {
            dto.setLastModified(LocalDateTime.ofInstant(
                    resource.getModified().toInstant(), ZoneId.systemDefault()));
        }
        
        setFileType(dto);
        return dto;
    }
    
    private FileDTO toDTO(FileMetadata metadata) {
        FileDTO dto = new FileDTO();
        dto.setId(metadata.getId());
        dto.setServerId(metadata.getServerId());
        dto.setPath(metadata.getPath());
        dto.setName(metadata.getName());
        dto.setIsDirectory(metadata.getIsDirectory());
        dto.setSize(metadata.getSize());
        dto.setContentType(metadata.getContentType());
        dto.setLastModified(metadata.getLastModified());
        setFileType(dto);
        return dto;
    }
    
    private void setFileType(FileDTO dto) {
        if (Boolean.TRUE.equals(dto.getIsDirectory())) {
            dto.setFileType("folder");
            dto.setIcon("folder");
            dto.setColor("#FFA726");
            return;
        }
        
        String filename = dto.getName();
        if (filename == null) return;
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            String ext = filename.substring(lastDot + 1).toLowerCase();
            Map<String, String> typeInfo = FILE_TYPE_MAP.getOrDefault(ext, 
                    Map.of("icon", "file", "color", "#95A5A6", "type", "other"));
            dto.setFileType(typeInfo.get("type"));
            dto.setIcon(typeInfo.get("icon"));
            dto.setColor(typeInfo.get("color"));
        }
    }
}