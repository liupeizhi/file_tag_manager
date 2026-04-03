package com.filemanager.service;

import com.filemanager.config.PasswordEncryptor;
import com.filemanager.dto.ServerConfigDTO;
import com.filemanager.entity.ServerConfig;
import com.filemanager.repository.FileMetadataRepository;
import com.filemanager.repository.ServerConfigRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServerService {
    
    @Autowired
    private ServerConfigRepository serverConfigRepository;
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    
    @Autowired
    private WebDavService webDavService;
    
    @Autowired
    private PasswordEncryptor passwordEncryptor;
    
    public List<ServerConfigDTO> getAllServers() {
        return serverConfigRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    public ServerConfigDTO getServerById(Long id) {
        ServerConfig server = serverConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("服务器不存在"));
        return toDTO(server);
    }
    
    @Transactional
    public ServerConfigDTO addServer(ServerConfigDTO dto) {
        ServerConfig server = new ServerConfig();
        copyFromDTO(dto, server);
        ServerConfig saved = serverConfigRepository.save(server);
        return toDTO(saved);
    }
    
    @Transactional
    public ServerConfigDTO updateServer(Long id, ServerConfigDTO dto) {
        ServerConfig server = serverConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("服务器不存在"));
        copyFromDTO(dto, server);
        ServerConfig saved = serverConfigRepository.save(server);
        return toDTO(saved);
    }
    
    private void copyFromDTO(ServerConfigDTO dto, ServerConfig server) {
        server.setName(dto.getName());
        server.setProtocol(dto.getProtocol() != null ? dto.getProtocol() : "webdav");
        server.setUrl(dto.getUrl());
        server.setUsername(dto.getUsername());
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            server.setPassword(passwordEncryptor.encrypt(dto.getPassword()));
        }
        server.setRootPath(dto.getRootPath() != null ? dto.getRootPath() : "/");
        server.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        server.setDescription(dto.getDescription());
        server.setExtraConfig(dto.getExtraConfig());
    }
    
    @Transactional
    public void deleteServer(Long id) {
        fileMetadataRepository.deleteByServerId(id);
        serverConfigRepository.deleteById(id);
    }
    
    public boolean testConnection(Long id) {
        ServerConfig server = serverConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("服务器不存在"));
        
        String password = server.getPassword() != null ? 
                passwordEncryptor.decrypt(server.getPassword()) : null;
        server.setPassword(password);
        
        return webDavService.testConnection(server);
    }
    
    private ServerConfigDTO toDTO(ServerConfig server) {
        ServerConfigDTO dto = new ServerConfigDTO();
        BeanUtils.copyProperties(server, dto);
        dto.setPassword(null);
        return dto;
    }
}