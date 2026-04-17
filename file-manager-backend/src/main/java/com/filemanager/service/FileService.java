package com.filemanager.service;

import com.filemanager.config.PasswordEncryptor;
import com.filemanager.dto.FileDTO;
import com.filemanager.dto.FileResource;
import com.filemanager.entity.FileMetadata;
import com.filemanager.entity.FileTagRelation;
import com.filemanager.entity.ServerConfig;
import com.filemanager.exception.ProtocolException;
import com.filemanager.repository.FileMetadataRepository;
import com.filemanager.repository.FileTagRelationRepository;
import com.filemanager.repository.ServerConfigRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FileService {
    
    @Autowired
    private ServerConfigRepository serverConfigRepository;
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    
    @Autowired
    private FileTagRelationRepository fileTagRelationRepository;
    
    @Autowired
    private Map<String, FileProtocolService> protocolServices;
    
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
    
    private FileProtocolService getProtocolService(ServerConfig server) {
        String protocol = server.getProtocol() != null ? server.getProtocol().toLowerCase() : "webdav";
        
        if (protocol.equals("http") || protocol.equals("https")) {
            protocol = "webdav";
        }
        
        String beanName = protocol.equals("webdav") ? "webDavService" : protocol + "Service";
        FileProtocolService service = protocolServices.get(beanName);
        if (service == null) {
            throw new ProtocolException(protocol, "init", "不支持的协议类型: " + protocol);
        }
        return service;
    }
    
    private String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) return "";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
    
    public List<FileDTO> getFileTree(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        List<FileResource> resources = service.listFiles(server, path);
        
        return resources.stream()
                .filter(r -> !r.getPath().equals(path))
                .map(r -> toDTO(r, serverId))
                .collect(Collectors.toList());
    }
    
    public Page<FileDTO> getFileList(Long serverId, String path, String name, String type,
                                      String startDate, String endDate,
                                      String sortBy, String sortOrder,
                                      int page, int size) {
        if (serverId == null) {
            return Page.empty();
        }
        
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        List<FileResource> resources = service.listFiles(server, path != null ? path : "/");
        
        List<FileDTO> allFiles = resources.stream()
                .filter(r -> !r.getPath().equals(path))
                .map(r -> toDTO(r, serverId))
                .collect(Collectors.toList());
        
        if (name != null && !name.isEmpty()) {
            allFiles = allFiles.stream()
                    .filter(f -> f.getName() != null && f.getName().toLowerCase().contains(name.toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (type != null && !type.isEmpty()) {
            allFiles = allFiles.stream()
                    .filter(f -> f.getFileType() != null && f.getFileType().equalsIgnoreCase(type))
                    .collect(Collectors.toList());
        }
        
        allFiles.sort((a, b) -> {
            if ("desc".equalsIgnoreCase(sortOrder)) {
                return compareFiles(b, a, sortBy);
            }
            return compareFiles(a, b, sortBy);
        });
        
        int total = allFiles.size();
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, total);
        
        List<FileDTO> pageContent = fromIndex < total ? 
                allFiles.subList(fromIndex, toIndex) : List.of();
        
        return new org.springframework.data.domain.PageImpl<>(
                pageContent, 
                PageRequest.of(page, size), 
                total);
    }
    
    private int compareFiles(FileDTO a, FileDTO b, String sortBy) {
        if (sortBy == null) sortBy = "name";
        
        switch (sortBy) {
            case "name":
                return String.CASE_INSENSITIVE_ORDER.compare(
                        a.getName() != null ? a.getName() : "",
                        b.getName() != null ? b.getName() : "");
            case "size":
                return Long.compare(a.getSize() != null ? a.getSize() : 0, 
                                   b.getSize() != null ? b.getSize() : 0);
            case "lastModified":
                if (a.getLastModified() == null && b.getLastModified() == null) return 0;
                if (a.getLastModified() == null) return -1;
                if (b.getLastModified() == null) return 1;
                return a.getLastModified().compareTo(b.getLastModified());
            default:
                return 0;
        }
    }
    
    @Transactional
    public Map<String, Integer> syncFiles(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        
        Map<String, Integer> result = new HashMap<>();
        result.put("added", 0);
        result.put("updated", 0);
        result.put("deleted", 0);
        
        Map<String, FileResource> fileResources = new HashMap<>();
        collectResources(service, server, path, fileResources);
        
        String pathPrefix = path.equals("/") ? "/" : path;
        List<FileMetadata> existingMetadata = fileMetadataRepository.findByServerId(serverId).stream()
                .filter(m -> m.getPath().startsWith(pathPrefix) || path.equals("/"))
                .collect(Collectors.toList());
        
        Map<String, FileMetadata> existingMap = existingMetadata.stream()
                .collect(Collectors.toMap(FileMetadata::getPath, m -> m));
        
        for (FileMetadata metadata : existingMetadata) {
            if (!fileResources.containsKey(metadata.getPath())) {
                fileTagRelationRepository.deleteByFileId(metadata.getId());
                fileTagRelationRepository.deleteByFilePathStartingWithAndServerId(metadata.getPath() + "/", serverId);
                fileMetadataRepository.delete(metadata);
                result.put("deleted", result.get("deleted") + 1);
            }
        }
        
        for (Map.Entry<String, FileResource> entry : fileResources.entrySet()) {
            String filePath = entry.getKey();
            FileResource resource = entry.getValue();
            
            if (existingMap.containsKey(filePath)) {
                FileMetadata metadata = existingMap.get(filePath);
                updateMetadataFromResource(metadata, resource);
                fileMetadataRepository.save(metadata);
                result.put("updated", result.get("updated") + 1);
            } else {
                FileMetadata metadata = createMetadataFromResource(serverId, resource);
                fileMetadataRepository.save(metadata);
                result.put("added", result.get("added") + 1);
            }
        }
        
        return result;
    }
    
    private void collectResources(FileProtocolService service, ServerConfig server, String path, Map<String, FileResource> allResources) {
        try {
            List<FileResource> resources = service.listFiles(server, path);
            for (FileResource resource : resources) {
                if (!resource.getPath().equals(path)) {
                    allResources.put(resource.getPath(), resource);
                    if (resource.isDirectory()) {
                        collectResources(service, server, resource.getPath(), allResources);
                    }
                }
            }
        } catch (Exception e) {
        }
    }
    
    public List<FileDTO> getAllFilesRecursive(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        
        Map<String, FileResource> allResources = new HashMap<>();
        collectResources(service, server, path, allResources);
        
        return allResources.values().stream()
                .map(r -> toDTO(r, serverId))
                .collect(Collectors.toList());
    }
    
    private void updateMetadataFromResource(FileMetadata metadata, FileResource resource) {
        metadata.setName(resource.getName());
        metadata.setIsDirectory(resource.isDirectory());
        metadata.setSize(resource.getSize());
        metadata.setContentType(resource.getContentType());
        metadata.setLastModified(resource.getLastModified());
    }
    
    private FileMetadata createMetadataFromResource(Long serverId, FileResource resource) {
        FileMetadata metadata = new FileMetadata();
        metadata.setServerId(serverId);
        metadata.setPath(resource.getPath());
        metadata.setName(resource.getName());
        metadata.setIsDirectory(resource.isDirectory());
        metadata.setSize(resource.getSize());
        metadata.setContentType(resource.getContentType());
        metadata.setLastModified(resource.getLastModified());
        return metadata;
    }
    
    public FileMetadata getOrCreateMetadata(Long serverId, String path) {
        Optional<FileMetadata> existing = fileMetadataRepository.findByServerIdAndPath(serverId, path);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        FileResource resource = service.getFileInfo(server, path);
        
        if (resource == null) {
            FileMetadata metadata = new FileMetadata();
            metadata.setServerId(serverId);
            metadata.setPath(path);
            metadata.setName(getFileNameFromPath(path));
            metadata.setIsDirectory(false);
            return fileMetadataRepository.save(metadata);
        }
        
        FileMetadata metadata = new FileMetadata();
        metadata.setServerId(serverId);
        metadata.setPath(path);
        metadata.setName(resource.getName());
        metadata.setIsDirectory(resource.isDirectory());
        metadata.setSize(resource.getSize());
        metadata.setContentType(resource.getContentType());
        metadata.setLastModified(resource.getLastModified());
        
        return fileMetadataRepository.save(metadata);
    }
    
    private String getFileNameFromPath(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }
    
    @Transactional
    public void uploadFile(Long serverId, String path, String filename, InputStream data) {
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        String fullPath = path.endsWith("/") ? path + filename : path + "/" + filename;
        service.uploadFile(server, fullPath, data);
    }
    
    public InputStream downloadFile(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        return service.downloadFile(server, path);
    }
    
    @Transactional
    public void renameFile(Long serverId, String oldPath, String newPath) {
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        service.move(server, oldPath, newPath);
    }
    
    @Transactional
    public void deleteFile(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        service.delete(server, path);
    }
    
    @Transactional
    public void createFolder(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        service.createDirectory(server, path);
    }
    
    private ServerConfig getServer(Long serverId) {
        ServerConfig server = serverConfigRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("服务器不存在"));
        
        if (server.getPassword() != null) {
            String decrypted = passwordEncryptor.decrypt(server.getPassword());
            if (decrypted != null) {
                server.setPassword(decrypted);
            }
        }
        
        return server;
    }
    
    private FileDTO toDTO(FileResource resource, Long serverId) {
        FileDTO dto = new FileDTO();
        dto.setServerId(serverId);
        dto.setPath(resource.getPath());
        dto.setName(resource.getName());
        dto.setIsDirectory(resource.isDirectory());
        dto.setSize(resource.getSize());
        dto.setContentType(resource.getContentType());
        dto.setLastModified(resource.getLastModified());
        
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