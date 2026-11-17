package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 访问历史。按 (用户, 服务器, 路径) 去重, 重复访问只更新时间,
 * 这样历史列表读起来是"最近访问过的位置"而不是流水账。
 */
@Data
@Entity
@Table(name = "access_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_history_user_server_path",
                columnNames = {"user_id", "server_id", "path"}))
public class AccessHistory {

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

    @Column(name = "accessed_at", nullable = false)
    private LocalDateTime accessedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        accessedAt = LocalDateTime.now();
        if (isDirectory == null) isDirectory = false;
    }
}
