package com.filemanager.service;

import com.filemanager.dto.FileResource;
import com.filemanager.entity.ServerConfig;
import com.filemanager.exception.ProtocolException;
import org.apache.commons.net.ftp.*;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;

@Service
public class FtpService implements FileProtocolService {
    
    private static final int DEFAULT_PORT = 21;
    private static final int TIMEOUT_MS = 30000;
    
    @Override
    public boolean testConnection(ServerConfig server) {
        try {
            FTPClient ftp = connect(server);
            ftp.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public List<FileResource> listFiles(ServerConfig server, String path) {
        try {
            FTPClient ftp = connect(server);
            String normalizedPath = normalizePath(path);
            
            FTPFile[] files = ftp.listFiles(normalizedPath);
            List<FileResource> result = new ArrayList<>();
            
            for (FTPFile file : files) {
                if (file.getName().equals(".") || file.getName().equals("..")) continue;
                
                FileResource fr = new FileResource();
                fr.setPath(normalizedPath + "/" + file.getName());
                fr.setName(file.getName());
                fr.setDirectory(file.isDirectory());
                fr.setSize(file.getSize());
                if (file.getTimestamp() != null) {
                    fr.setLastModified(LocalDateTime.ofInstant(
                            file.getTimestamp().toInstant(), ZoneId.systemDefault()));
                }
                fr.setContentType(file.getType() == FTPFile.FILE_TYPE ? "application/octet-stream" : null);
                result.add(fr);
            }
            
            disconnect(ftp);
            return result;
        } catch (Exception e) {
            throw new ProtocolException("ftp", "listFiles", e.getMessage());
        }
    }
    
    @Override
    public InputStream downloadFile(ServerConfig server, String path) {
        try {
            FTPClient ftp = connect(server);
            String normalizedPath = normalizePath(path);
            InputStream stream = ftp.retrieveFileStream(normalizedPath);
            return new FtpInputStream(ftp, stream);
        } catch (Exception e) {
            throw new ProtocolException("ftp", "downloadFile", e.getMessage());
        }
    }
    
    @Override
    public void uploadFile(ServerConfig server, String path, InputStream data) {
        try {
            FTPClient ftp = connect(server);
            String normalizedPath = normalizePath(path);
            ftp.storeFile(normalizedPath, data);
            disconnect(ftp);
        } catch (Exception e) {
            throw new ProtocolException("ftp", "uploadFile", e.getMessage());
        }
    }
    
    @Override
    public void createDirectory(ServerConfig server, String path) {
        try {
            FTPClient ftp = connect(server);
            String normalizedPath = normalizePath(path);
            ftp.makeDirectory(normalizedPath);
            disconnect(ftp);
        } catch (Exception e) {
            throw new ProtocolException("ftp", "createDirectory", e.getMessage());
        }
    }
    
    @Override
    public void delete(ServerConfig server, String path) {
        try {
            FTPClient ftp = connect(server);
            String normalizedPath = normalizePath(path);
            ftp.deleteFile(normalizedPath);
            disconnect(ftp);
        } catch (Exception e) {
            throw new ProtocolException("ftp", "delete", e.getMessage());
        }
    }
    
    @Override
    public void move(ServerConfig server, String from, String to) {
        try {
            FTPClient ftp = connect(server);
            String fromPath = normalizePath(from);
            String toPath = normalizePath(to);
            ftp.rename(fromPath, toPath);
            disconnect(ftp);
        } catch (Exception e) {
            throw new ProtocolException("ftp", "move", e.getMessage());
        }
    }
    
    @Override
    public FileResource getFileInfo(ServerConfig server, String path) {
        try {
            FTPClient ftp = connect(server);
            String normalizedPath = normalizePath(path);
            FTPFile[] files = ftp.listFiles(normalizedPath);
            
            if (files != null && files.length > 0) {
                FTPFile file = files[0];
                FileResource fr = new FileResource();
                fr.setPath(normalizedPath);
                fr.setName(file.getName());
                fr.setDirectory(file.isDirectory());
                fr.setSize(file.getSize());
                if (file.getTimestamp() != null) {
                    fr.setLastModified(LocalDateTime.ofInstant(
                            file.getTimestamp().toInstant(), ZoneId.systemDefault()));
                }
                disconnect(ftp);
                return fr;
            }
            
            disconnect(ftp);
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private FTPClient connect(ServerConfig server) throws IOException {
        FTPClient ftp = new FTPClient();
        ftp.setDefaultTimeout(TIMEOUT_MS);
        ftp.setDataTimeout(TIMEOUT_MS);
        
        int port = server.getPort() != null ? server.getPort() : DEFAULT_PORT;
        ftp.connect(server.getHost(), port);
        
        if (!ftp.login(server.getUsername(), server.getPassword())) {
            throw new IOException("FTP login failed");
        }
        
        if (server.getPassiveMode() == null || server.getPassiveMode()) {
            ftp.enterLocalPassiveMode();
        }
        
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        return ftp;
    }
    
    private void disconnect(FTPClient ftp) {
        if (ftp != null && ftp.isConnected()) {
            try {
                ftp.logout();
                ftp.disconnect();
            } catch (Exception e) {
            }
        }
    }
    
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) return "/" + path;
        return path;
    }
    
    private String getNameFromPath(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }
    
    private static class FtpInputStream extends InputStream {
        private final FTPClient ftp;
        private final InputStream delegate;
        
        public FtpInputStream(FTPClient ftp, InputStream delegate) {
            this.ftp = ftp;
            this.delegate = delegate;
        }
        
        @Override
        public int read() throws IOException {
            return delegate.read();
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }
        
        @Override
        public void close() throws IOException {
            delegate.close();
            ftp.completePendingCommand();
            if (ftp != null && ftp.isConnected()) {
                try {
                    ftp.logout();
                    ftp.disconnect();
                } catch (Exception e) {
                }
            }
        }
    }
}