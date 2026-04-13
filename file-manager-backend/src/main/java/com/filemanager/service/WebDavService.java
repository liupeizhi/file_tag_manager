package com.filemanager.service;

import com.filemanager.entity.ServerConfig;
import com.filemanager.dto.FileResource;
import com.filemanager.exception.ProtocolException;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.DavResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WebDavService implements FileProtocolService {
    
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
    
    @Override
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
    
    @Override
    public List<FileResource> listFiles(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            List<DavResource> resources = sardine.list(url);
            
            return resources.stream()
                    .map(this::toFileResource)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ProtocolException("webdav", "listFiles", e.getMessage());
        }
    }
    
    private FileResource toFileResource(DavResource resource) {
        FileResource fr = new FileResource();
        fr.setPath(resource.getPath());
        fr.setName(getNameFromPath(resource.getPath()));
        fr.setDirectory(resource.isDirectory());
        fr.setSize(resource.getContentLength() != null ? resource.getContentLength() : 0);
        fr.setContentType(resource.getContentType());
        if (resource.getModified() != null) {
            fr.setLastModified(LocalDateTime.ofInstant(resource.getModified().toInstant(), ZoneId.systemDefault()));
        }
        return fr;
    }
    
    private String getNameFromPath(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }
    
    @Override
    public InputStream downloadFile(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            return sardine.get(url);
        } catch (IOException e) {
            throw new ProtocolException("webdav", "downloadFile", e.getMessage());
        }
    }
    
    @Override
    public void uploadFile(ServerConfig server, String path, InputStream data) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            byte[] bytes = data.readAllBytes();
            sardine.put(url, bytes);
        } catch (IOException e) {
            throw new ProtocolException("webdav", "uploadFile", e.getMessage());
        }
    }
    
    @Override
    public void createDirectory(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            sardine.createDirectory(url);
        } catch (IOException e) {
            throw new ProtocolException("webdav", "createDirectory", e.getMessage());
        }
    }
    
    @Override
    public void delete(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            sardine.delete(url);
        } catch (IOException e) {
            throw new ProtocolException("webdav", "delete", e.getMessage());
        }
    }
    
    @Override
    public void move(ServerConfig server, String from, String to) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String fromUrl = server.getUrl() + encodePath(from);
            String toUrl = server.getUrl() + encodePath(to);
            sardine.move(fromUrl, toUrl);
        } catch (IOException e) {
            throw new ProtocolException("webdav", "move", e.getMessage());
        }
    }
    
    @Override
    public FileResource getFileInfo(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String encodedPath = encodePath(path);
            String url = server.getUrl() + encodedPath;
            
            List<DavResource> resources = sardine.list(url, 0);
            if (resources != null && !resources.isEmpty()) {
                DavResource resource = resources.stream()
                        .filter(r -> r.getPath().equals(encodedPath) || r.getPath().endsWith(encodedPath))
                        .findFirst()
                        .orElse(resources.get(0));
                return toFileResource(resource);
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}