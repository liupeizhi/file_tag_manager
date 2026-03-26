package com.filemanager.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FileDTO {
    private Long id;
    private Long serverId;
    private String path;
    private String name;
    private Boolean isDirectory;
    private Long size;
    private String contentType;
    private LocalDateTime lastModified;
    private String fileType;
    private String icon;
    private String color;
}