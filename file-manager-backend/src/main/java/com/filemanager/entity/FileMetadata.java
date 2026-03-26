package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "file_metadata", indexes = {
    @Index(name = "idx_server_path", columnList = "server_id,path"),
    @Index(name = "idx_name", columnList = "name"),
    @Index(name = "idx_content_type", columnList = "content_type"),
    @Index(name = "idx_last_modified", columnList = "last_modified")
})
public class FileMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "server_id", nullable = false)
    private Long serverId;
    
    @Column(nullable = false, length = 1000)
    private String path;
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(name = "is_directory")
    private Boolean isDirectory = false;
    
    private Long size;
    
    @Column(name = "content_type", length = 100)
    private String contentType;
    
    @Column(name = "last_modified")
    private LocalDateTime lastModified;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", insertable = false, updatable = false)
    private ServerConfig server;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}