package com.filemanager.service;

import com.filemanager.dto.FileResource;
import com.filemanager.entity.ServerConfig;
import com.filemanager.exception.ProtocolException;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

@Service
public class SmbService implements FileProtocolService {

    private static final int DEFAULT_PORT = 445;
    
    @Override
    public boolean testConnection(ServerConfig server) {
        try (Connection connection = connect(server)) {
            return connection.isConnected();
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public List<FileResource> listFiles(ServerConfig server, String path) {
        try {
            DiskShare share = getShare(server);
            String normalizedPath = normalizePath(path);
            
            List<FileResource> result = new ArrayList<>();
            List<FileIdBothDirectoryInformation> files = share.list(normalizedPath);
            
            for (FileIdBothDirectoryInformation file : files) {
                String fileName = file.getFileName();
                if (fileName.equals(".") || fileName.equals("..")) continue;
                
                FileResource fr = new FileResource();
                fr.setPath(normalizedPath + "\\" + fileName);
                fr.setName(fileName);
                fr.setDirectory((file.getFileAttributes() & 0x10) != 0);
                fr.setSize(file.getEndOfFile());
                if (file.getLastWriteTime() != null) {
                    fr.setLastModified(LocalDateTime.ofInstant(
                            file.getLastWriteTime().toInstant(), ZoneId.systemDefault()));
                }
                result.add(fr);
            }
            
            return result;
        } catch (Exception e) {
            throw new ProtocolException("smb", "listFiles", e.getMessage());
        }
    }
    
    @Override
    public InputStream downloadFile(ServerConfig server, String path) {
        try {
            DiskShare share = getShare(server);
            String normalizedPath = normalizePath(path);
            com.hierynomus.smbj.share.File smbFile = share.openFile(
                normalizedPath,
                Set.of(AccessMask.GENERIC_READ),
                Set.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                Set.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                Set.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            );
            return smbFile.getInputStream();
        } catch (Exception e) {
            throw new ProtocolException("smb", "downloadFile", e.getMessage());
        }
    }
    
    @Override
    public void uploadFile(ServerConfig server, String path, InputStream data) {
        try {
            DiskShare share = getShare(server);
            String normalizedPath = normalizePath(path);
            com.hierynomus.smbj.share.File smbFile = share.openFile(
                normalizedPath,
                Set.of(AccessMask.GENERIC_WRITE),
                Set.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                Set.of(SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                Set.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            );
            smbFile.getOutputStream().write(data.readAllBytes());
            smbFile.close();
        } catch (Exception e) {
            throw new ProtocolException("smb", "uploadFile", e.getMessage());
        }
    }
    
    @Override
    public void createDirectory(ServerConfig server, String path) {
        try {
            DiskShare share = getShare(server);
            String normalizedPath = normalizePath(path);
            share.mkdir(normalizedPath);
        } catch (Exception e) {
            throw new ProtocolException("smb", "createDirectory", e.getMessage());
        }
    }
    
    @Override
    public void delete(ServerConfig server, String path) {
        try {
            DiskShare share = getShare(server);
            String normalizedPath = normalizePath(path);
            share.rm(normalizedPath);
        } catch (Exception e) {
            throw new ProtocolException("smb", "delete", e.getMessage());
        }
    }
    
    @Override
    public void move(ServerConfig server, String from, String to) {
        try {
            DiskShare share = getShare(server);
            String fromPath = normalizePath(from);
            String toPath = normalizePath(to);
            com.hierynomus.smbj.share.File smbFile = share.openFile(
                fromPath,
                Set.of(AccessMask.GENERIC_ALL),
                Set.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                Set.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                SMB2CreateDisposition.FILE_OPEN,
                Set.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            );
            smbFile.rename(toPath);
            smbFile.close();
        } catch (Exception e) {
            throw new ProtocolException("smb", "move", e.getMessage());
        }
    }
    
    @Override
    public FileResource getFileInfo(ServerConfig server, String path) {
        try {
            DiskShare share = getShare(server);
            String normalizedPath = normalizePath(path);
            com.hierynomus.msfscc.fileinformation.FileAllInformation info = share.getFileInformation(normalizedPath);
            
            FileResource fr = new FileResource();
            fr.setPath(normalizedPath);
            fr.setName(getNameFromPath(normalizedPath));
            fr.setDirectory(info.getStandardInformation().isDirectory());
            fr.setSize(info.getStandardInformation().getEndOfFile());
            if (info.getBasicInformation().getLastWriteTime() != null) {
                fr.setLastModified(LocalDateTime.ofInstant(
                        info.getBasicInformation().getLastWriteTime().toInstant(), ZoneId.systemDefault()));
            }
            
            return fr;
        } catch (Exception e) {
            return null;
        }
    }
    
    private Connection connect(ServerConfig server) throws IOException {
        SMBClient client = new SMBClient();
        int port = server.getPort() != null ? server.getPort() : DEFAULT_PORT;

        Connection connection = client.connect(server.getHost(), port);

        AuthenticationContext ac = new AuthenticationContext(
                server.getUsername(),
                server.getPassword() != null ? server.getPassword().toCharArray() : new char[0],
                server.getDomain() != null ? server.getDomain() : "");

        connection.authenticate(ac);
        return connection;
    }
    
    private DiskShare getShare(ServerConfig server) throws IOException {
        SMBClient client = new SMBClient();
        int port = server.getPort() != null ? server.getPort() : DEFAULT_PORT;
        
        Connection connection = client.connect(server.getHost(), port);
        AuthenticationContext ac = new AuthenticationContext(
                server.getUsername(),
                server.getPassword() != null ? server.getPassword().toCharArray() : new char[0],
                server.getDomain() != null ? server.getDomain() : "");
        Session session = connection.authenticate(ac);
        return (DiskShare) session.connectShare(server.getShareName());
    }
    
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "\\";
        if (!path.startsWith("\\")) return "\\" + path.replace("/", "\\");
        return path.replace("/", "\\");
    }
    
    private String getNameFromPath(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.endsWith("\\") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = normalized.lastIndexOf('\\');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }
}