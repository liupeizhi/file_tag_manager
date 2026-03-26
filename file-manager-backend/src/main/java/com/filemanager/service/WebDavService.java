package com.filemanager.service;

import com.filemanager.entity.ServerConfig;
import com.filemanager.exception.WebDavException;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.DavResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class WebDavService {
    
    public Sardine createSardine(String username, String password) {
        return SardineFactory.begin(username, password);
    }
    
    public boolean testConnection(ServerConfig server) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            return sardine.exists(server.getUrl());
        } catch (Exception e) {
            return false;
        }
    }
    
    public List<DavResource> listFiles(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + path;
            return sardine.list(url);
        } catch (IOException e) {
            throw new WebDavException("获取文件列表失败: " + e.getMessage(), e);
        }
    }
    
    public InputStream downloadFile(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + path;
            return sardine.get(url);
        } catch (IOException e) {
            throw new WebDavException("下载文件失败: " + e.getMessage(), e);
        }
    }
    
    public void uploadFile(ServerConfig server, String path, InputStream data) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + path;
            sardine.put(url, data);
        } catch (IOException e) {
            throw new WebDavException("上传文件失败: " + e.getMessage(), e);
        }
    }
    
    public void createDirectory(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + path;
            sardine.createDirectory(url);
        } catch (IOException e) {
            throw new WebDavException("创建目录失败: " + e.getMessage(), e);
        }
    }
    
    public void delete(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + path;
            sardine.delete(url);
        } catch (IOException e) {
            throw new WebDavException("删除失败: " + e.getMessage(), e);
        }
    }
    
    public void move(ServerConfig server, String from, String to) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String fromUrl = server.getUrl() + from;
            String toUrl = server.getUrl() + to;
            sardine.move(fromUrl, toUrl);
        } catch (IOException e) {
            throw new WebDavException("移动/重命名失败: " + e.getMessage(), e);
        }
    }
}