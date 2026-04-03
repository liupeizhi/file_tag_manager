package com.filemanager.service;

import com.filemanager.entity.ServerConfig;
import com.filemanager.exception.WebDavException;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.DavResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class WebDavService {
    
    public Sardine createSardine(String username, String password) {
        System.setProperty("http.proxyHost", "");
        System.setProperty("http.proxyPort", "");
        System.setProperty("https.proxyHost", "");
        System.setProperty("https.proxyPort", "");
        System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]");
        
        Sardine sardine = SardineFactory.begin(username, password);
        sardine.enablePreemptiveAuthentication("http");
        sardine.enablePreemptiveAuthentication("https");
        return sardine;
    }
    
    public boolean testConnection(ServerConfig server) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            return sardine.exists(server.getUrl());
        } catch (Exception e) {
            return false;
        }
    }
    
    private String encodePath(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] parts = path.split("/", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) result.append("/");
            if (!parts[i].isEmpty()) {
                result.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
        return result.toString();
    }
    
    public List<DavResource> listFiles(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            return sardine.list(url);
        } catch (IOException e) {
            throw new WebDavException("获取文件列表失败: " + e.getMessage(), e);
        }
    }
    
    public InputStream downloadFile(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            return sardine.get(url);
        } catch (IOException e) {
            throw new WebDavException("下载文件失败: " + e.getMessage(), e);
        }
    }
    
    public void uploadFile(ServerConfig server, String path, InputStream data) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            
            byte[] bytes = data.readAllBytes();
            
            sardine.put(url, bytes);
        } catch (IOException e) {
            throw new WebDavException("上传文件失败: " + e.getMessage(), e);
        }
    }
    
    public void createDirectory(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            sardine.createDirectory(url);
        } catch (IOException e) {
            throw new WebDavException("创建目录失败: " + e.getMessage(), e);
        }
    }
    
    public void delete(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            sardine.delete(url);
        } catch (IOException e) {
            throw new WebDavException("删除失败: " + e.getMessage(), e);
        }
    }
    
    public void move(ServerConfig server, String from, String to) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String fromUrl = server.getUrl() + encodePath(from);
            String toUrl = server.getUrl() + encodePath(to);
            sardine.move(fromUrl, toUrl);
        } catch (IOException e) {
            throw new WebDavException("移动/重命名失败: " + e.getMessage(), e);
        }
    }
    
    public DavResource getFileInfo(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String encodedPath = encodePath(path);
            String url = server.getUrl() + encodedPath;
            
            List<DavResource> resources = sardine.list(url, 0);
            if (resources != null && !resources.isEmpty()) {
                String expectedPath = encodedPath;
                if (!expectedPath.startsWith("/")) {
                    expectedPath = "/" + expectedPath;
                }
                
                for (DavResource resource : resources) {
                    String resourcePath = resource.getPath();
                    if (resourcePath.endsWith(expectedPath) || 
                        resourcePath.equals(expectedPath) ||
                        resourcePath.endsWith(encodedPath)) {
                        return resource;
                    }
                }
                if (resources.size() > 1) {
                    return resources.get(1);
                }
                return resources.get(0);
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}