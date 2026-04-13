package com.filemanager.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FileResource {
    private String path;
    private String name;
    private boolean isDirectory;
    private long size;
    private LocalDateTime lastModified;
    private String contentType;
    
    public FileResource() {}
    
    public FileResource(String path, String name, boolean isDirectory, long size, LocalDateTime lastModified, String contentType) {
        this.path = path;
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
        this.contentType = contentType;
    }
}