package com.filemanager.service;

import com.filemanager.config.PasswordEncryptor;
import com.filemanager.dto.ServerConfigDTO;
import com.filemanager.entity.ServerConfig;
import com.filemanager.entity.User;
import com.filemanager.enums.Role;
import com.filemanager.repository.FileMetadataRepository;
import com.filemanager.repository.ServerConfigRepository;
import com.filemanager.repository.UserRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ServerService {
    
    @Autowired
    private ServerConfigRepository serverConfigRepository;
    
    @Autowired
    private FileMetadataRepository fileMetadataRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private Map<String, FileProtocolService> protocolServices;
    
    @Autowired
    private PasswordEncryptor passwordEncryptor;
    
    public List<ServerConfigDTO> getAllServers() {
        User currentUser = getCurrentUser();
        
        if (currentUser.getRole() == Role.ADMIN) {
            return serverConfigRepository.findAll().stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        } else {
            return serverConfigRepository.findByUserId(currentUser.getId()).stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }
    }
    
    public ServerConfigDTO getServerById(Long id) {
        ServerConfig server = serverConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("服务器不存在"));
        
        checkPermission(server);
        return toDTO(server);
    }
    
    @Transactional
    public ServerConfigDTO addServer(ServerConfigDTO dto) {
        User currentUser = getCurrentUser();
        
        ServerConfig server = new ServerConfig();
        copyFromDTO(dto, server);
        server.setUserId(currentUser.getId());
        ServerConfig saved = serverConfigRepository.save(server);
        return toDTO(saved);
    }
    
    @Transactional
    public ServerConfigDTO updateServer(Long id, ServerConfigDTO dto) {
        ServerConfig server = serverConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("服务器不存在"));
        
        checkPermission(server);
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
        server.setHost(dto.getHost());
        server.setPort(dto.getPort());
        server.setShareName(dto.getShareName());
        server.setDomain(dto.getDomain());
        server.setPrivateKey(dto.getPrivateKey());
        server.setPassiveMode(dto.getPassiveMode() != null ? dto.getPassiveMode() : true);
    }
    
    @Transactional
    public void deleteServer(Long id) {
        ServerConfig server = serverConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("服务器不存在"));
        
        checkPermission(server);
        fileMetadataRepository.deleteByServerId(id);
        serverConfigRepository.deleteById(id);
    }
    
    public boolean testConnection(Long id) {
        ServerConfig server = serverConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("服务器不存在"));
        
        checkPermission(server);
        
        String password = server.getPassword() != null ? 
                passwordEncryptor.decrypt(server.getPassword()) : null;
        server.setPassword(password);
        
        String protocol = server.getProtocol() != null ? server.getProtocol().toLowerCase() : "webdav";
        FileProtocolService service = getProtocolService(protocol);
        return service.testConnection(server);
    }
    
    private FileProtocolService getProtocolService(String protocol) {
        String beanName = protocol.equals("webdav") ? "webDavService" : protocol + "Service";
        return protocolServices.get(beanName);
    }
    
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("未登录");
        }
        String username = auth.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
    
    private void checkPermission(ServerConfig server) {
        User currentUser = getCurrentUser();
        
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        
        if (server.getUserId() == null || !server.getUserId().equals(currentUser.getId())) {
            throw new RuntimeException("无权限操作此服务器");
        }
    }
    
    private ServerConfigDTO toDTO(ServerConfig server) {
        ServerConfigDTO dto = new ServerConfigDTO();
        BeanUtils.copyProperties(server, dto);
        dto.setPassword(null);
        dto.setHost(server.getHost());
        dto.setPort(server.getPort());
        dto.setShareName(server.getShareName());
        dto.setDomain(server.getDomain());
        dto.setPrivateKey(server.getPrivateKey());
        dto.setPassiveMode(server.getPassiveMode());
        dto.setUserId(server.getUserId());
        return dto;
    }
}