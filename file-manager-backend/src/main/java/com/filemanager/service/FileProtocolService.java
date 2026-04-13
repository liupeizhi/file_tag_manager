package com.filemanager.service;

import com.filemanager.entity.ServerConfig;
import com.filemanager.dto.FileResource;
import java.io.InputStream;
import java.util.List;

public interface FileProtocolService {
    
    boolean testConnection(ServerConfig server);
    
    List<FileResource> listFiles(ServerConfig server, String path);
    
    InputStream downloadFile(ServerConfig server, String path);
    
    void uploadFile(ServerConfig server, String path, InputStream data);
    
    void createDirectory(ServerConfig server, String path);
    
    void delete(ServerConfig server, String path);
    
    void move(ServerConfig server, String from, String to);
    
    FileResource getFileInfo(ServerConfig server, String path);
}