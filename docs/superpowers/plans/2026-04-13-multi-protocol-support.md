# 多协议文件访问支持 - 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 扩展文件管理系统支持 SMB、SFTP、FTP 协议，实现多协议统一文件访问

**Architecture:** 策略模式 - FileProtocolService接口定义统一操作，各协议独立实现类，FileService根据protocol动态选择服务

**Tech Stack:** 
- 后端: Spring Boot 3, smbj (SMB), JSch (SFTP), Apache Commons Net (FTP)
- 前端: Vue 3, Element Plus

---

## Phase 1: 后端基础设施

### Task 1: 添加Maven依赖

**Files:**
- Modify: `file-manager-backend/pom.xml`

- [ ] **Step 1: 在pom.xml中添加SMB/SFTP/FTP依赖**

在 `</dependencies>` 标签前添加：

```xml
<!-- SMB协议 - smbj -->
<dependency>
    <groupId>com.hierynomus</groupId>
    <artifactId>smbj</artifactId>
    <version>0.13.0</version>
</dependency>

<!-- SFTP协议 - JSch -->
<dependency>
    <groupId>com.jcraft</groupId>
    <artifactId>jsch</artifactId>
    <version>0.1.55</version>
</dependency>

<!-- FTP协议 - Apache Commons Net -->
<dependency>
    <groupId>commons-net</groupId>
    <artifactId>commons-net</artifactId>
    <version>3.11</version>
</dependency>
```

位置：在 `sardine` 依赖之后，`jakarta.xml.bind` 依赖之前

- [ ] **Step 2: 验证依赖添加成功**

Run: `cd file-manager-backend && mvn dependency:resolve -q`
Expected: 无错误，依赖解析成功

- [ ] **Step 3: Commit**

```bash
git add file-manager-backend/pom.xml
git commit -m "feat: add SMB/SFTP/FTP library dependencies"
```

### Task 2: 创建FileResource类

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/dto/FileResource.java`

- [ ] **Step 1: 创建FileResource类**

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/dto/FileResource.java
git commit -m "feat: add FileResource DTO for unified file representation"
```

### Task 3: 创建FileProtocolService接口

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/service/FileProtocolService.java`

- [ ] **Step 1: 创建FileProtocolService接口**

```java
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
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/service/FileProtocolService.java
git commit -m "feat: add FileProtocolService interface for multi-protocol support"
```

### Task 4: 创建ProtocolException异常类

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/exception/ProtocolException.java`

- [ ] **Step 1: 创建ProtocolException类**

```java
package com.filemanager.exception;

public class ProtocolException extends RuntimeException {
    private String protocol;
    private String operation;
    
    public ProtocolException(String protocol, String operation, String message) {
        super(protocol + " " + operation + " 失败: " + message);
        this.protocol = protocol;
        this.operation = operation;
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public String getOperation() {
        return operation;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/exception/ProtocolException.java
git commit -m "feat: add ProtocolException for protocol operation errors"
```

### Task 5: 扩展ServerConfig实体

**Files:**
- Modify: `file-manager-backend/src/main/java/com/filemanager/entity/ServerConfig.java`

- [ ] **Step 1: 在ServerConfig类中添加新字段**

在现有字段后添加：

```java
@Column(length = 255)
private String host;

private Integer port;

@Column(length = 255)
private String shareName;

@Column(length = 100)
private String domain;

@Column(columnDefinition = "TEXT")
private String privateKey;

private Boolean passiveMode = true;
```

在 `@PrePersist` 方法中添加默认值初始化：

```java
@PrePersist
protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
    if (protocol == null) protocol = "webdav";
    if (rootPath == null) rootPath = "/";
    if (enabled == null) enabled = true;
    if (passiveMode == null) passiveMode = true;
}
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/entity/ServerConfig.java
git commit -m "feat: extend ServerConfig with SMB/SFTP/FTP fields"
```

### Task 6: 执行数据库迁移

**Files:**
- Create: SQL migration script (手动执行)

- [ ] **Step 1: 执行数据库ALTER语句**

Run: 在MySQL中执行以下SQL：

```sql
ALTER TABLE server_config 
ADD COLUMN host VARCHAR(255) COMMENT '主机地址(SMB/SFTP/FTP)',
ADD COLUMN port INT COMMENT '端口(SMB:445/SFTP:22/FTP:21)',
ADD COLUMN share_name VARCHAR(255) COMMENT 'SMB共享名',
ADD COLUMN domain VARCHAR(100) COMMENT 'SMB域',
ADD COLUMN private_key TEXT COMMENT 'SFTP私钥(加密存储)',
ADD COLUMN passive_mode BOOLEAN DEFAULT TRUE COMMENT 'FTP被动模式';
```

Run: `docker exec -i trade_stat_db mysql -uroot -p123456 file_manager -e "ALTER TABLE server_config ADD COLUMN host VARCHAR(255), ADD COLUMN port INT, ADD COLUMN share_name VARCHAR(255), ADD COLUMN domain VARCHAR(100), ADD COLUMN private_key TEXT, ADD COLUMN passive_mode BOOLEAN DEFAULT TRUE;"`
Expected: 无错误

- [ ] **Step 2: 验证表结构**

Run: `docker exec trade_stat_db mysql -uroot -p123456 -e "DESCRIBE file_manager.server_config;"`
Expected: 显示新增字段

### Task 7: 扩展ServerConfigDTO

**Files:**
- Modify: `file-manager-backend/src/main/java/com/filemanager/dto/ServerConfigDTO.java`

- [ ] **Step 1: 在ServerConfigDTO中添加新字段**

在现有字段后添加：

```java
private String host;
private Integer port;
private String shareName;
private String domain;
private String privateKey;
private Boolean passiveMode;
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/dto/ServerConfigDTO.java
git commit -m "feat: extend ServerConfigDTO with protocol-specific fields"
```

---

## Phase 2: 协议服务实现

### Task 8: 改造WebDavService实现接口

**Files:**
- Modify: `file-manager-backend/src/main/java/com/filemanager/service/WebDavService.java`

- [ ] **Step 1: 让WebDavService实现FileProtocolService接口**

修改类声明：

```java
@Service
public class WebDavService implements FileProtocolService {
```

添加 import：

```java
import com.filemanager.dto.FileResource;
import com.filemanager.service.FileProtocolService;
import java.time.ZoneId;
```

- [ ] **Step 2: 改造testConnection方法添加@Override**

```java
@Override
public boolean testConnection(ServerConfig server) {
    try {
        Sardine sardine = createSardine(server.getUsername(), server.getPassword());
        return sardine.exists(server.getUrl());
    } catch (Exception e) {
        return false;
    }
}
```

- [ ] **Step 3: 改造listFiles方法返回List<FileResource>**

```java
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
```

- [ ] **Step 4: 改造downloadFile方法添加@Override**

```java
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
```

- [ ] **Step 5: 改造uploadFile方法添加@Override**

```java
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
```

- [ ] **Step 6: 改造createDirectory方法添加@Override**

```java
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
```

- [ ] **Step 7: 改造delete方法添加@Override**

```java
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
```

- [ ] **Step 8: 改造move方法添加@Override**

```java
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
```

- [ ] **Step 9: 改造getFileInfo方法添加@Override**

```java
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
```

- [ ] **Step 10: 编译验证**

Run: `cd file-manager-backend && mvn compile -q`
Expected: 无错误

- [ ] **Step 11: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/service/WebDavService.java
git commit -m "refactor: WebDavService implements FileProtocolService interface"
```

### Task 9: 创建SmbService

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/service/SmbService.java`

- [ ] **Step 1: 创建SmbService类**

```java
package com.filemanager.service;

import com.filemanager.dto.FileResource;
import com.filemanager.entity.ServerConfig;
import com.filemanager.exception.ProtocolException;
import com.hierynomus.msfscc.fileinformation.FileStandardInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class SmbService implements FileProtocolService {
    
    private static final int DEFAULT_PORT = 445;
    private static final int TIMEOUT_MS = 30000;
    
    @Override
    public boolean testConnection(ServerConfig server) {
        try {
            Connection connection = connect(server);
            connection.close();
            return true;
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
            List<FileStandardInformation> files = share.list(normalizedPath);
            
            for (FileStandardInformation file : files) {
                FileResource fr = new FileResource();
                fr.setPath(normalizedPath + "/" + file.getFileName());
                fr.setName(file.getFileName());
                fr.setDirectory(file.isDirectory());
                fr.setSize(file.getEndOfFile());
                if (file.getLastWriteTime() != null) {
                    fr.setLastModified(LocalDateTime.ofInstant(
                            file.getLastWriteTime().toInstant(), ZoneId.systemDefault()));
                }
                result.add(fr);
            }
            
            share.close();
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
            File file = share.openFile(normalizedPath, 
                    com.hierynomus.smbj.share.File.READ_ONLY, 
                    null, 
                    com.hierynomus.smbj.share.File.SHARING_READ);
            InputStream stream = file.getInputStream();
            share.close();
            return stream;
        } catch (Exception e) {
            throw new ProtocolException("smb", "downloadFile", e.getMessage());
        }
    }
    
    @Override
    public void uploadFile(ServerConfig server, String path, InputStream data) {
        try {
            DiskShare share = getShare(server);
            String normalizedPath = normalizePath(path);
            File file = share.openFile(normalizedPath,
                    com.hierynomus.smbj.share.File.WRITE_ONLY,
                    null,
                    com.hierynomus.smbj.share.File.SHARING_WRITE);
            file.getOutputStream().write(data.readAllBytes());
            file.close();
            share.close();
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
            share.close();
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
            share.close();
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
            share.rename(fromPath, toPath);
            share.close();
        } catch (Exception e) {
            throw new ProtocolException("smb", "move", e.getMessage());
        }
    }
    
    @Override
    public FileResource getFileInfo(ServerConfig server, String path) {
        try {
            DiskShare share = getShare(server);
            String normalizedPath = normalizePath(path);
            FileStandardInformation info = share.getFileInformation(normalizedPath);
            
            FileResource fr = new FileResource();
            fr.setPath(normalizedPath);
            fr.setName(getNameFromPath(normalizedPath));
            fr.setDirectory(info.isDirectory());
            fr.setSize(info.getEndOfFile());
            if (info.getLastWriteTime() != null) {
                fr.setLastModified(LocalDateTime.ofInstant(
                        info.getLastWriteTime().toInstant(), ZoneId.systemDefault()));
            }
            
            share.close();
            return fr;
        } catch (Exception e) {
            return null;
        }
    }
    
    private Connection connect(ServerConfig server) throws IOException {
        SMBClient client = new SMBClient();
        int port = server.getPort() != null ? server.getPort() : DEFAULT_PORT;
        
        Connection connection = client.connect(server.getHost(), port);
        connection.setConfig(new com.hierynomus.smbj.connection.config.ConnectionConfig.Builder()
                .withTimeout(TIMEOUT_MS)
                .build());
        
        AuthenticationContext ac = new AuthenticationContext(
                server.getUsername(),
                server.getPassword() != null ? server.getPassword().toCharArray() : new char[0],
                server.getDomain() != null ? server.getDomain() : "");
        
        Session session = connection.authenticate(ac);
        return connection;
    }
    
    private DiskShare getShare(ServerConfig server) throws IOException {
        Connection connection = connect(server);
        AuthenticationContext ac = new AuthenticationContext(
                server.getUsername(),
                server.getPassword() != null ? server.getPassword().toCharArray() : new char[0],
                server.getDomain() != null ? server.getDomain() : "");
        Session session = connection.authenticate(ac);
        return (DiskShare) session.connectShare(server.getShareName());
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
}
```

- [ ] **Step 2: 编译验证**

Run: `cd file-manager-backend && mvn compile -q`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/service/SmbService.java
git commit -m "feat: add SmbService implementing FileProtocolService"
```

### Task 10: 创建SftpService

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/service/SftpService.java`

- [ ] **Step 1: 创建SftpService类**

```java
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
            
            Vector<LsEntry> entries = sftp.ls(normalizedPath);
            List<FileResource> result = new ArrayList<>();
            
            for (LsEntry entry : entries) {
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
            // Note: 需要在stream关闭后调用disconnect，这里返回stream，调用者需负责关闭连接
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
        
        // 私钥认证
        if (server.getPrivateKey() != null && !server.getPrivateKey().isEmpty()) {
            jsch.addIdentity("sftp_key", server.getPrivateKey().getBytes(), null, null);
        }
        
        Session session = jsch.getSession(server.getUsername(), server.getHost(), port);
        
        // 密码认证
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
                // ignore
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
    
    // 辅助类：包装SFTP InputStream，关闭时自动断开连接
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
                    // ignore
                }
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd file-manager-backend && mvn compile -q`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/service/SftpService.java
git commit -m "feat: add SftpService implementing FileProtocolService"
```

### Task 11: 创建FtpService

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/service/FtpService.java`

- [ ] **Step 1: 创建FtpService类**

```java
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
                // ignore
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
    
    // 辅助类：包装FTP InputStream，关闭时自动断开连接
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
                    // ignore
                }
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd file-manager-backend && mvn compile -q`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/service/FtpService.java
git commit -m "feat: add FtpService implementing FileProtocolService"
```

---

## Phase 3: FileService改造

### Task 12: 改造FileService使用协议服务

**Files:**
- Modify: `file-manager-backend/src/main/java/com/filemanager/service/FileService.java`

- [ ] **Step 1: 添加protocolServices依赖注入**

在FileService类中添加：

```java
@Autowired
private Map<String, FileProtocolService> protocolServices;
```

添加 import：

```java
import com.filemanager.dto.FileResource;
import com.filemanager.service.FileProtocolService;
import com.filemanager.exception.ProtocolException;
import java.util.Map;
```

- [ ] **Step 2: 添加getProtocolService方法**

```java
private FileProtocolService getProtocolService(ServerConfig server) {
    String protocol = server.getProtocol() != null ? server.getProtocol().toLowerCase() : "webdav";
    String beanName = protocol.equals("webdav") ? "webDavService" : protocol + "Service";
    FileProtocolService service = protocolServices.get(beanName);
    if (service == null) {
        throw new ProtocolException(protocol, "init", "不支持的协议类型: " + protocol);
    }
    return service;
}
```

- [ ] **Step 3: 添加FileResource到FileDTO转换方法**

```java
private FileDTO toDTO(FileResource resource, Long serverId) {
    FileDTO dto = new FileDTO();
    dto.setServerId(serverId);
    dto.setPath(resource.getPath());
    dto.setName(resource.getName());
    dto.setIsDirectory(resource.isDirectory());
    dto.setSize(resource.getSize());
    dto.setLastModified(resource.getLastModified());
    
    // 计算文件类型信息
    String ext = getExtension(resource.getName());
    Map<String, String> typeInfo = FILE_TYPE_MAP.getOrDefault(ext, 
            Map.of("icon", "file", "color", "#607D8B", "type", "other"));
    dto.setFileType(typeInfo.get("type"));
    
    return dto;
}

private String getExtension(String filename) {
    if (filename == null || filename.isEmpty()) return "";
    int dot = filename.lastIndexOf('.');
    return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "";
}
```

- [ ] **Step 4: 改造getFileTree方法**

```java
public List<FileDTO> getFileTree(Long serverId, String path) {
    ServerConfig server = getServer(serverId);
    FileProtocolService service = getProtocolService(server);
    List<FileResource> resources = service.listFiles(server, path);
    
    return resources.stream()
            .filter(r -> !r.getPath().equals(path))
            .map(r -> toDTO(r, serverId))
            .collect(Collectors.toList());
}
```

- [ ] **Step 5: 改造getFileList方法**

替换原方法中的WebDAV调用部分：

```java
public Page<FileDTO> getFileList(Long serverId, String path, String name, String type,
                                  String startDate, String endDate,
                                  String sortBy, String sortOrder,
                                  int page, int size) {
    if (serverId == null) {
        return Page.empty();
    }
    
    ServerConfig server = getServer(serverId);
    FileProtocolService service = getProtocolService(server);
    List<FileResource> resources = service.listFiles(server, path != null ? path : "/");
    
    List<FileDTO> allFiles = resources.stream()
            .filter(r -> !r.getPath().equals(path))
            .map(r -> toDTO(r, serverId))
            .collect(Collectors.toList());
    
    // 后续过滤和排序逻辑保持不变...
```

- [ ] **Step 6: 改造downloadFile方法**

```java
public InputStream downloadFile(Long serverId, String path) {
    ServerConfig server = getServer(serverId);
    FileProtocolService service = getProtocolService(server);
    return service.downloadFile(server, path);
}
```

- [ ] **Step 7: 改造uploadFile方法**

```java
public void uploadFile(Long serverId, String path, InputStream data) {
    ServerConfig server = getServer(serverId);
    FileProtocolService service = getProtocolService(server);
    service.uploadFile(server, path, data);
}
```

- [ ] **Step 8: 改造createFolder方法**

```java
public void createFolder(Long serverId, String path, String name) {
    ServerConfig server = getServer(serverId);
    FileProtocolService service = getProtocolService(server);
    String newPath = path.endsWith("/") ? path + name : path + "/" + name;
    service.createDirectory(server, newPath);
}
```

- [ ] **Step 9: 改造deleteFile方法**

```java
public void deleteFile(Long serverId, String path) {
    ServerConfig server = getServer(serverId);
    FileProtocolService service = getProtocolService(server);
    service.delete(server, path);
}
```

- [ ] **Step 10: 改造renameFile方法**

```java
public void renameFile(Long serverId, String path, String newName) {
    ServerConfig server = getServer(serverId);
    FileProtocolService service = getProtocolService(server);
    String parentPath = path.substring(0, path.lastIndexOf('/') + 1);
    String newPath = parentPath + newName;
    service.move(server, path, newPath);
}
```

- [ ] **Step 11: 编译验证**

Run: `cd file-manager-backend && mvn compile -q`
Expected: 无错误

- [ ] **Step 12: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/service/FileService.java
git commit -m "refactor: FileService uses FileProtocolService for multi-protocol support"
```

### Task 13: 改造ServerService支持新字段

**Files:**
- Modify: `file-manager-backend/src/main/java/com/filemanager/service/ServerService.java`

- [ ] **Step 1: 在toDTO方法中添加新字段映射**

找到 `toDTO` 方法，添加：

```java
dto.setHost(server.getHost());
dto.setPort(server.getPort());
dto.setShareName(server.getShareName());
dto.setDomain(server.getDomain());
dto.setPrivateKey(server.getPrivateKey());
dto.setPassiveMode(server.getPassiveMode());
```

- [ ] **Step 2: 在create/update方法中处理新字段**

在创建和更新服务器的方法中添加新字段处理：

```java
server.setHost(dto.getHost());
server.setPort(dto.getPort());
server.setShareName(dto.getShareName());
server.setDomain(dto.getDomain());
server.setPrivateKey(dto.getPrivateKey());
server.setPassiveMode(dto.getPassiveMode());
```

- [ ] **Step 3: 改造testConnection方法**

```java
public boolean testConnection(Long id) {
    ServerConfig server = serverConfigRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("服务器不存在"));
    
    String protocol = server.getProtocol() != null ? server.getProtocol().toLowerCase() : "webdav";
    FileProtocolService service = getProtocolService(protocol);
    return service.testConnection(server);
}

private FileProtocolService getProtocolService(String protocol) {
    String beanName = protocol.equals("webdav") ? "webDavService" : protocol + "Service";
    return protocolServices.get(beanName);
}
```

添加依赖注入：

```java
@Autowired
private Map<String, FileProtocolService> protocolServices;
```

- [ ] **Step 4: 编译验证**

Run: `cd file-manager-backend && mvn compile -q`
Expected: 无错误

- [ ] **Step 5: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/service/ServerService.java
git commit -m "refactor: ServerService supports new protocol fields"
```

---

## Phase 4: 前端改造

### Task 14: 改造ServerDialog为动态表单

**Files:**
- Modify: `file-manager-frontend/src/components/server/ServerDialog.vue`

- [ ] **Step 1: 重写整个ServerDialog组件**

```vue
<template>
  <el-dialog
    v-model="visible"
    title="服务器配置"
    width="600px"
    @close="resetForm"
  >
    <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
      <el-form-item label="服务器名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入服务器名称" />
      </el-form-item>
      
      <el-form-item label="协议类型" prop="protocol">
        <el-select v-model="form.protocol" @change="onProtocolChange">
          <el-option label="WebDAV" value="webdav" />
          <el-option label="SMB" value="smb" />
          <el-option label="SFTP" value="sftp" />
          <el-option label="FTP" value="ftp" />
        </el-select>
      </el-form-item>
      
      <!-- 动态字段 -->
      <template v-for="field in currentFields" :key="field.key">
        <el-form-item 
          v-if="shouldShowField(field)"
          :label="field.label"
          :prop="field.required ? field.key : null"
        >
          <el-input v-if="field.type === 'text'"
            v-model="form[field.key]"
            :placeholder="field.placeholder"
          />
          <el-input v-if="field.type === 'password'"
            v-model="form[field.key]"
            type="password"
            :placeholder="field.placeholder"
            show-password
          />
          <el-input-number v-if="field.type === 'number'"
            v-model="form[field.key]"
            :min="1"
            :max="65535"
            controls-position="right"
          />
          <el-select v-if="field.type === 'select'"
            v-model="form[field.key]"
          >
            <el-option v-for="opt in field.options" :key="opt" :label="opt" :value="opt" />
          </el-select>
          <el-input v-if="field.type === 'textarea'"
            v-model="form[field.key]"
            type="textarea"
            :rows="field.rows || 5"
            :placeholder="field.placeholder"
          />
          <el-checkbox v-if="field.type === 'checkbox'"
            v-model="form[field.key]"
          >
            {{ field.help || '' }}
          </el-checkbox>
        </el-form-item>
      </template>
      
      <el-form-item label="根路径">
        <el-input v-model="form.rootPath" placeholder="/" />
      </el-form-item>
      
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleTest" :loading="testing">测试连接</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { useServerStore } from '@/store/server'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const props = defineProps({
  modelValue: Boolean,
  server: Object
})

const emit = defineEmits(['update:modelValue', 'saved'])

const visible = ref(false)
const loading = ref(false)
const testing = ref(false)
const formRef = ref(null)
const serverStore = useServerStore()
const isEdit = ref(false)

const protocolFields = {
  webdav: [
    { key: 'url', label: '服务器地址', type: 'text', required: true, placeholder: 'https://nas.example.com/webdav' }
  ],
  smb: [
    { key: 'host', label: '主机地址', type: 'text', required: true, placeholder: '192.168.1.100' },
    { key: 'port', label: '端口', type: 'number', default: 445 },
    { key: 'shareName', label: '共享名', type: 'text', required: true, placeholder: 'share' },
    { key: 'domain', label: '域', type: 'text', placeholder: 'WORKGROUP' }
  ],
  sftp: [
    { key: 'host', label: '主机地址', type: 'text', required: true },
    { key: 'port', label: '端口', type: 'number', default: 22 },
    { key: 'authType', label: '认证方式', type: 'select', options: ['password', 'privateKey'], default: 'password' },
    { key: 'privateKey', label: '私钥', type: 'textarea', rows: 5, showIf: { authType: 'privateKey' }, placeholder: '-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----' }
  ],
  ftp: [
    { key: 'host', label: '主机地址', type: 'text', required: true },
    { key: 'port', label: '端口', type: 'number', default: 21 },
    { key: 'passiveMode', label: '被动模式', type: 'checkbox', default: true, help: '防火墙环境下建议启用' }
  ]
}

const commonFields = ['name', 'protocol', 'username', 'password', 'rootPath', 'description', 'enabled']

const form = ref(getDefaultForm())

function getDefaultForm() {
  return {
    name: '',
    protocol: 'webdav',
    url: '',
    host: '',
    port: null,
    shareName: '',
    domain: '',
    username: '',
    password: '',
    privateKey: '',
    passiveMode: true,
    authType: 'password',
    rootPath: '/',
    description: '',
    enabled: true
  }
}

const currentFields = computed(() => {
  const fields = protocolFields[form.value.protocol] || []
  const common = [
    { key: 'username', label: '用户名', type: 'text', required: true },
    { key: 'password', label: '密码', type: 'password', required: form.value.protocol !== 'sftp' || form.value.authType === 'password' }
  ]
  return [...fields, ...common]
})

const shouldShowField = (field) => {
  if (!field.showIf) return true
  for (const [key, value] of Object.entries(field.showIf)) {
    if (form.value[key] !== value) return false
  }
  return true
}

const rules = computed(() => {
  const r = {
    name: [{ required: true, message: '请输入服务器名称', trigger: 'blur' }],
    protocol: [{ required: true, message: '请选择协议类型', trigger: 'change' }]
  }
  currentFields.value.forEach(field => {
    if (field.required && shouldShowField(field)) {
      r[field.key] = [{ required: true, message: `请输入${field.label}`, trigger: 'blur' }]
    }
  })
  return r
})

function onProtocolChange() {
  const newForm = getDefaultForm()
  commonFields.forEach(key => {
    newForm[key] = form.value[key]
  })
  newForm.protocol = form.value.protocol
  
  currentFields.value.forEach(field => {
    if (field.default !== undefined && newForm[field.key] === null) {
      newForm[field.key] = field.default
    }
  })
  
  form.value = newForm
}

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

watch(() => props.server, (server) => {
  if (server && visible.value) {
    isEdit.value = true
    form.value = { ...getDefaultForm(), ...server }
  }
})

async function handleTest() {
  await formRef.value.validate()
  
  testing.value = true
  try {
    const response = await request({
      url: `/api/servers/test`,
      method: 'post',
      data: form.value
    })
    if (response.code === 200 && response.data) {
      ElMessage.success('连接成功')
    } else {
      ElMessage.error('连接失败: ' + (response.message || '未知错误'))
    }
  } catch (error) {
    ElMessage.error('测试失败: ' + error.message)
  } finally {
    testing.value = false
  }
}

async function handleSubmit() {
  await formRef.value.validate()
  
  loading.value = true
  try {
    if (isEdit.value) {
      await request({
        url: `/api/servers/${form.value.id}`,
        method: 'put',
        data: form.value
      })
      ElMessage.success('更新成功')
    } else {
      await serverStore.add(form.value)
      ElMessage.success('添加成功')
    }
    visible.value = false
    resetForm()
    emit('saved')
  } catch (error) {
    ElMessage.error('操作失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.value = getDefaultForm()
  isEdit.value = false
  formRef.value?.resetFields()
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-frontend/src/components/server/ServerDialog.vue
git commit -m "feat: ServerDialog with dynamic form for multi-protocol"
```

### Task 15: 改造ServerManage显示协议类型

**Files:**
- Modify: `file-manager-frontend/src/views/admin/ServerManage.vue`

- [ ] **Step 1: 添加协议类型列**

在表格列定义中添加：

```vue
<el-table-column prop="protocol" label="协议" width="100">
  <template #default="{ row }">
    <el-tag :type="getProtocolTagType(row.protocol)">
      {{ getProtocolLabel(row.protocol) }}
    </el-tag>
  </template>
</el-table-column>
```

在 script 中添加方法：

```javascript
function getProtocolTagType(protocol) {
  const types = {
    webdav: 'primary',
    smb: 'success',
    sftp: 'warning',
    ftp: 'info'
  }
  return types[protocol] || 'info'
}

function getProtocolLabel(protocol) {
  const labels = {
    webdav: 'WebDAV',
    smb: 'SMB',
    sftp: 'SFTP',
    ftp: 'FTP'
  }
  return labels[protocol] || protocol
}
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-frontend/src/views/admin/ServerManage.vue
git commit -m "feat: ServerManage shows protocol type with colored tags"
```

### Task 16: 更新ServerStore支持新字段

**Files:**
- Modify: `file-manager-frontend/src/store/server.js`

- [ ] **Step 1: 确保store处理所有新字段**

检查 `server.js` 中 add/update 方法是否正确处理新字段，确保不遗漏：

```javascript
async add(server) {
  const response = await request({
    url: '/api/servers',
    method: 'post',
    data: {
      name: server.name,
      protocol: server.protocol,
      url: server.url,
      host: server.host,
      port: server.port,
      shareName: server.shareName,
      domain: server.domain,
      username: server.username,
      password: server.password,
      privateKey: server.privateKey,
      passiveMode: server.passiveMode,
      rootPath: server.rootPath,
      description: server.description,
      enabled: server.enabled
    }
  })
  // ...
}
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-frontend/src/store/server.js
git commit -m "fix: ServerStore handles all protocol fields"
```

---

## Phase 5: 后端API补充

### Task 17: 添加ServerController测试连接API

**Files:**
- Modify: `file-manager-backend/src/main/java/com/filemanager/controller/ServerController.java`

- [ ] **Step 1: 添加测试连接API**

```java
@PostMapping("/test")
public ApiResponse<Boolean> testConnection(@RequestBody ServerConfigDTO dto) {
    // 创建临时ServerConfig对象
    ServerConfig server = new ServerConfig();
    server.setProtocol(dto.getProtocol());
    server.setUrl(dto.getUrl());
    server.setHost(dto.getHost());
    server.setPort(dto.getPort());
    server.setShareName(dto.getShareName());
    server.setDomain(dto.getDomain());
    server.setUsername(dto.getUsername());
    
    // 解密密码（如果是加密的）或直接使用
    if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
        try {
            server.setPassword(passwordEncryptor.decrypt(dto.getPassword()));
        } catch (Exception e) {
            server.setPassword(dto.getPassword());
        }
    }
    
    if (dto.getPrivateKey() != null) {
        try {
            server.setPrivateKey(passwordEncryptor.decrypt(dto.getPrivateKey()));
        } catch (Exception e) {
            server.setPrivateKey(dto.getPrivateKey());
        }
    }
    
    server.setPassiveMode(dto.getPassiveMode());
    
    boolean success = serverService.testConnection(server);
    return ApiResponse.success(success);
}
```

添加依赖注入：

```java
@Autowired
private PasswordEncryptor passwordEncryptor;
```

- [ ] **Step 2: 编译验证**

Run: `cd file-manager-backend && mvn compile -q`
Expected: 无错误

- [ ] **Step 3: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/controller/ServerController.java
git commit -m "feat: add server connection test API endpoint"
```

---

## Phase 6: 验证与测试

### Task 18: 启动服务验证

- [ ] **Step 1: 启动后端服务**

Run: `cd file-manager-backend && mvn spring-boot:run -q`
Expected: 服务正常启动在1001端口

- [ ] **Step 2: 启动前端服务**

Run: `cd file-manager-frontend && npm run dev`
Expected: 前端启动，可访问

- [ ] **Step 3: 测试WebDAV连接**

通过前端添加WebDAV服务器，验证原有功能正常

- [ ] **Step 4: 测试各协议表单显示**

切换协议类型，验证动态表单正确显示对应字段

---

## 实施说明

**执行顺序：** 严格按照任务编号顺序执行（Task 1 → Task 18）

**每个任务的步骤：** 每个任务内的步骤也必须按顺序执行

**验证要求：**
- 每个Step执行后都要验证结果
- 如果遇到编译错误，必须修复后才能继续下一步
- 代码必须编译通过后才能commit

**注意事项：**
1. 确保数据库迁移SQL已执行
2. 注意ServerConfig实体字段与数据库字段名称映射（share_name vs shareName）
3. 密码加密存储需要PasswordEncryptor
4. 各协议服务需要正确处理连接超时

**完成标志：**
- 所有18个任务完成
- 所有代码已提交
- 前端可以正确显示各协议配置表单
- 后端各协议服务可以正确连接对应服务器