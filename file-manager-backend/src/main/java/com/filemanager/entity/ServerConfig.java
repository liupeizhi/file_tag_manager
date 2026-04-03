package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "server_config")
public class ServerConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(length = 20)
    private String protocol = "webdav";
    
    @Column(nullable = false, length = 500)
    private String url;
    
    @Column(length = 100)
    private String username;
    
    @Column(length = 255)
    private String password;
    
    @Column(length = 500)
    private String rootPath = "/";
    
    @Column(name = "is_enabled")
    private Boolean enabled = true;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String extraConfig;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (protocol == null) protocol = "webdav";
        if (rootPath == null) rootPath = "/";
        if (enabled == null) enabled = true;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}