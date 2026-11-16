package com.filemanager.service;

import com.filemanager.entity.ServerConfig;
import com.filemanager.dto.FileResource;
import java.io.InputStream;
import java.util.List;

public interface FileProtocolService {
    
    boolean testConnection(ServerConfig server);
    
    List<FileResource> listFiles(ServerConfig server, String path);
    
    InputStream downloadFile(ServerConfig server, String path);

    /**
     * 按字节区间下载。默认实现忽略区间, 返回完整文件。
     * 支持 Range 的协议(WebDAV)可覆写以启用断点续传与视频拖动。
     */
    default InputStream downloadFile(ServerConfig server, String path, Long start, Long end) {
        return downloadFile(server, path);
    }
    
    void uploadFile(ServerConfig server, String path, InputStream data);
    
    void createDirectory(ServerConfig server, String path);
    
    void delete(ServerConfig server, String path);
    
    void move(ServerConfig server, String from, String to);
    
    FileResource getFileInfo(ServerConfig server, String path);
}