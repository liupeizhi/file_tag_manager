package com.filemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_theme")
public class UserTheme {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "font_size", nullable = false)
    private Integer fontSize = 14;

    /** 亮度: system / light / dark */
    @Column(name = "brightness", length = 20)
    private String brightness;

    /** 风格: ink / comic */
    @Column(name = "style", length = 20)
    private String style;

    @Column(name = "text_color", length = 20)
    private String textColor;

    @Column(name = "accent_color", length = 20)
    private String accentColor;

    @Column(name = "background_filename", length = 255)
    private String backgroundFilename;

    @Column(name = "background_original_name", length = 255)
    private String backgroundOriginalName;

    @Column(name = "background_mime", length = 100)
    private String backgroundMime;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (fontSize == null) fontSize = 14;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
