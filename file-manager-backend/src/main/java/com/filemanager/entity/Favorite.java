package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 收藏。按 (用户, 服务器, 路径) 去重, 同一位置只保留一条。
 */
@Data
@Entity
@Table(name = "favorite",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_favorite_user_server_path",
                columnNames = {"user_id", "server_id", "path"}))
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(nullable = false, length = 700)
    private String path;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "is_directory")
    private Boolean isDirectory = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isDirectory == null) isDirectory = false;
    }
}
