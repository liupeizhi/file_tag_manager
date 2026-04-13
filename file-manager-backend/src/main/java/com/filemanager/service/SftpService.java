package com.filemanager.service;

import com.filemanager.dto.FileResource;
import com.filemanager.entity.ServerConfig;
import com.filemanager.exception.ProtocolException;
import com.jcraft.jsch.*;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;

@Service
public class SftpService implements FileProtocolService {
    
    private static final int DEFAULT_PORT = 22;
    private static final int TIMEOUT_MS = 30000;
    
    @Override
    public boolean testConnection(ServerConfig server) {
        try {
            ChannelSftp sftp = connect(server);
            disconnect(sftp);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public List<FileResource> listFiles(ServerConfig server, String path) {
        try {
            ChannelSftp sftp = connect(server);
            String normalizedPath = normalizePath(path);
            
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> entries = sftp.ls(normalizedPath);
            List<FileResource> result = new ArrayList<>();
            
            for (ChannelSftp.LsEntry entry : entries) {
                if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) continue;
                
                FileResource fr = new FileResource();
                fr.setPath(normalizedPath + "/" + entry.getFilename());
                fr.setName(entry.getFilename());
                fr.setDirectory(entry.getAttrs().isDir());
                fr.setSize(entry.getAttrs().getSize());
                if (entry.getAttrs().getMTime() > 0) {
                    fr.setLastModified(LocalDateTime.ofEpochSecond(entry.getAttrs().getMTime(), 0, ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now())));
                }
                result.add(fr);
            }
            
            disconnect(sftp);
            return result;
        } catch (Exception e) {
            throw new ProtocolException("sftp", "listFiles", e.getMessage());
        }
    }
    
    @Override
    public InputStream downloadFile(ServerConfig server, String path) {
        try {
            ChannelSftp sftp = connect(server);
            String normalizedPath = normalizePath(path);
            InputStream stream = sftp.get(normalizedPath);
            return new SftpInputStream(sftp, stream);
        } catch (Exception e) {
            throw new ProtocolException("sftp", "downloadFile", e.getMessage());
        }
    }
    
    @Override
    public void uploadFile(ServerConfig server, String path, InputStream data) {
        try {
            ChannelSftp sftp = connect(server);
            String normalizedPath = normalizePath(path);
            sftp.put(data, normalizedPath);
            disconnect(sftp);
        } catch (Exception e) {
            throw new ProtocolException("sftp", "uploadFile", e.getMessage());
        }
    }
    
    @Override
    public void createDirectory(ServerConfig server, String path) {
        try {
            ChannelSftp sftp = connect(server);
            String normalizedPath = normalizePath(path);
            sftp.mkdir(normalizedPath);
            disconnect(sftp);
        } catch (Exception e) {
            throw new ProtocolException("sftp", "createDirectory", e.getMessage());
        }
    }
    
    @Override
    public void delete(ServerConfig server, String path) {
        try {
            ChannelSftp sftp = connect(server);
            String normalizedPath = normalizePath(path);
            sftp.rm(normalizedPath);
            disconnect(sftp);
        } catch (Exception e) {
            throw new ProtocolException("sftp", "delete", e.getMessage());
        }
    }
    
    @Override
    public void move(ServerConfig server, String from, String to) {
        try {
            ChannelSftp sftp = connect(server);
            String fromPath = normalizePath(from);
            String toPath = normalizePath(to);
            sftp.rename(fromPath, toPath);
            disconnect(sftp);
        } catch (Exception e) {
            throw new ProtocolException("sftp", "move", e.getMessage());
        }
    }
    
    @Override
    public FileResource getFileInfo(ServerConfig server, String path) {
        try {
            ChannelSftp sftp = connect(server);
            String normalizedPath = normalizePath(path);
            SftpATTRS attrs = sftp.stat(normalizedPath);
            
            FileResource fr = new FileResource();
            fr.setPath(normalizedPath);
            fr.setName(getNameFromPath(normalizedPath));
            fr.setDirectory(attrs.isDir());
            fr.setSize(attrs.getSize());
            if (attrs.getMTime() > 0) {
                fr.setLastModified(LocalDateTime.ofEpochSecond(attrs.getMTime(), 0, ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now())));
            }
            
            disconnect(sftp);
            return fr;
        } catch (Exception e) {
            return null;
        }
    }
    
    private ChannelSftp connect(ServerConfig server) throws JSchException {
        JSch jsch = new JSch();
        
        int port = server.getPort() != null ? server.getPort() : DEFAULT_PORT;
        
        if (server.getPrivateKey() != null && !server.getPrivateKey().isEmpty()) {
            jsch.addIdentity("sftp_key", server.getPrivateKey().getBytes(), null, null);
        }
        
        Session session = jsch.getSession(server.getUsername(), server.getHost(), port);
        
        if (server.getPassword() != null && !server.getPassword().isEmpty()) {
            session.setPassword(server.getPassword());
        }
        
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(TIMEOUT_MS);
        session.connect();
        
        Channel channel = session.openChannel("sftp");
        channel.connect();
        return (ChannelSftp) channel;
    }
    
    private void disconnect(ChannelSftp sftp) {
        if (sftp != null) {
            try {
                sftp.disconnect();
                if (sftp.getSession() != null) {
                    sftp.getSession().disconnect();
                }
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
    
    private static class SftpInputStream extends InputStream {
        private final ChannelSftp sftp;
        private final InputStream delegate;
        
        public SftpInputStream(ChannelSftp sftp, InputStream delegate) {
            this.sftp = sftp;
            this.delegate = delegate;
        }
        
        @Override
        public int read() throws java.io.IOException {
            return delegate.read();
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            return delegate.read(b, off, len);
        }
        
        @Override
        public void close() throws java.io.IOException {
            delegate.close();
            if (sftp != null) {
                try {
                    sftp.disconnect();
                    if (sftp.getSession() != null) {
                        sftp.getSession().disconnect();
                    }
                } catch (Exception e) {
                }
            }
        }
    }
}