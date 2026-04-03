package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "file_tag_relation")
public class FileTagRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "file_path", nullable = false, length = 700)
    private String filePath;
    
    @Column(name = "server_id", nullable = false)
    private Long serverId;
    
    @Column(name = "tag_id", nullable = false)
    private Long tagId;
    
    @Column(name = "file_id")
    private Long fileId;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", insertable = false, updatable = false)
    private FileMetadata fileMetadata;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}