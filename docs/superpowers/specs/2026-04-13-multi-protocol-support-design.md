# 多协议文件访问支持 - 设计文档

**项目名称:** 文件标签浏览器 - 多协议扩展  
**创建日期:** 2026-04-13  
**技术栈:** Spring Boot 3 + Vue 3 + MySQL  
**目标:** 扩展支持 SMB、SFTP、FTP 协议

---

## 1. 项目概述

### 1.1 项目目标

将现有的 WebDAV 文件管理系统扩展为多协议支持，新增：
- **SMB (Server Message Block)** - Windows/NAS局域网共享协议
- **SFTP (SSH File Transfer Protocol)** - SSH加密文件传输
- **FTP (File Transfer Protocol)** - 传统文件传输协议

### 1.2 核心价值

- **统一管理** - 一个界面管理多种协议的远程文件
- **灵活选择** - 根据场景选择最适合的协议
- **无缝切换** - 协议切换无需改变使用习惯

### 1.3 不实现内容

- **Rsync** - 暂不实现，后续作为独立备份模块扩展

---

## 2. 整体架构

### 2.1 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     FileService                              │
│            (根据protocol调用对应ProtocolService)              │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│               FileProtocolService (接口)                     │
│  - listFiles(server, path)                                   │
│  - downloadFile(server, path)                                │
│  - uploadFile(server, path, data)                            │
│  - createDirectory(server, path)                             │
│  - delete(server, path)                                      │
│  - move(server, from, to)                                    │
│  - testConnection(server)                                    │
│  - getFileInfo(server, path)                                 │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────┬──────────────┬──────────────┬──────────────┐
│WebDavService │ SmbService   │ SftpService  │ FtpService   │
│ (现有sardine)│ (smbj库)     │ (JSch库)     │ (Apache FTP) │
│ 实现接口     │ 实现接口     │ 实现接口     │ 实现接口     │
└──────────────┴──────────────┴──────────────┴──────────────┘
```

### 2.2 架构说明

- **策略模式** - FileProtocolService接口定义统一操作，各协议独立实现
- **依赖注入** - Spring自动注入所有FileProtocolService实现，按协议名称获取
- **最小改动** - FileService通过协议类型动态选择服务，其他层不变

---

## 3. 数据库设计

### 3.1 server_config 表扩展

```sql
-- 新增字段
ALTER TABLE server_config 
ADD COLUMN host VARCHAR(255) COMMENT '主机地址(SMB/SFTP/FTP)',
ADD COLUMN port INT COMMENT '端口(SMB:445/SFTP:22/FTP:21)',
ADD COLUMN share_name VARCHAR(255) COMMENT 'SMB共享名',
ADD COLUMN domain VARCHAR(100) COMMENT 'SMB域(可选)',
ADD COLUMN private_key TEXT COMMENT 'SFTP私钥(加密存储)',
ADD COLUMN passive_mode BOOLEAN DEFAULT TRUE COMMENT 'FTP被动模式';

-- protocol字段说明
-- 现有默认值: webdav
-- 新增可选值: smb, sftp, ftp
```

### 3.2 字段使用规则

| 字段 | WebDAV | SMB | SFTP | FTP |
|------|--------|-----|------|-----|
| url | 必填 | 不用 | 不用 | 不用 |
| host | 不用 | 必填 | 必填 | 必填 |
| port | 不用 | 445(默认) | 22(默认) | 21(默认) |
| share_name | 不用 | 必填 | 不用 | 不用 |
| domain | 不用 | 可选 | 不用 | 不用 |
| username | 必填 | 必填 | 必填 | 必填 |
| password | 必填 | 必填 | 可选 | 必填 |
| private_key | 不用 | 不用 | 可选 | 不用 |
| passive_mode | 不用 | 不用 | 不用 | 默认true |
| root_path | 可选 | 可选 | 可选 | 可选 |

---

## 4. 后端设计

### 4.1 新增依赖 (pom.xml)

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

### 4.2 FileProtocolService 接口

```java
package com.filemanager.service;

import com.filemanager.entity.ServerConfig;
import java.io.InputStream;
import java.util.List;

public interface FileProtocolService {
    
    /**
     * 测试服务器连接
     */
    boolean testConnection(ServerConfig server);
    
    /**
     * 列出指定路径下的文件和目录
     */
    List<FileResource> listFiles(ServerConfig server, String path);
    
    /**
     * 下载文件
     */
    InputStream downloadFile(ServerConfig server, String path);
    
    /**
     * 上传文件
     */
    void uploadFile(ServerConfig server, String path, InputStream data);
    
    /**
     * 创建目录
     */
    void createDirectory(ServerConfig server, String path);
    
    /**
     * 删除文件或目录
     */
    void delete(ServerConfig server, String path);
    
    /**
     * 移动/重命名文件或目录
     */
    void move(ServerConfig server, String from, String to);
    
    /**
     * 获取文件信息
     */
    FileResource getFileInfo(ServerConfig server, String path);
}
```

### 4.3 FileResource 统一文件资源类

```java
package com.filemanager.dto;

import java.time.LocalDateTime;

public class FileResource {
    private String path;           // 完整路径
    private String name;           // 文件名
    private boolean isDirectory;   // 是否目录
    private long size;             // 文件大小(字节)
    private LocalDateTime lastModified; // 最后修改时间
    private String contentType;    // 内容类型
    
    // constructors, getters, setters
}
```

### 4.4 WebDavService 改造

现有 WebDavService 需要改造以实现 FileProtocolService 接口：

```java
@Service
public class WebDavService implements FileProtocolService {
    
    @Override
    public boolean testConnection(ServerConfig server) {
        // 现有实现保持不变
    }
    
    @Override
    public List<FileResource> listFiles(ServerConfig server, String path) {
        // 返回类型从 List<DavResource> 改为 List<FileResource>
        // 内部转换：DavResource -> FileResource
    }
    
    // 其他方法类似改造，返回类型统一为 FileResource
}
```

### 4.5 SmbService 实现

```java
package com.filemanager.service;

import com.hierynomus.msfscc.fileinformation.FileStandardInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.util.List;

@Service
public class SmbService implements FileProtocolService {
    
    private SMBClient smbClient;
    
    public SmbService() {
        this.smbClient = new SMBClient();
    }
    
    @Override
    public boolean testConnection(ServerConfig server) {
        try {
            Connection connection = smbClient.connect(server.getHost(), server.getPort());
            Session session = connection.getSession(
                new AuthenticationContext(
                    server.getUsername(),
                    server.getPassword().toCharArray(),
                    server.getDomain()
                )
            );
            DiskShare share = (DiskShare) session.connectShare(server.getShareName());
            share.close();
            session.close();
            connection.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public List<FileResource> listFiles(ServerConfig server, String path) {
        // 使用 DiskShare.list() 获取文件列表
        // 转换为 FileResource
    }
    
    @Override
    public InputStream downloadFile(ServerConfig server, String path) {
        // 使用 DiskShare.openFile() 获取 InputStream
    }
    
    // 其他方法实现
}
```

### 4.6 SftpService 实现

```java
package com.filemanager.service;

import com.jcraft.jsch.*;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.util.Vector;

@Service
public class SftpService implements FileProtocolService {
    
    private ChannelSftp connect(ServerConfig server) throws JSchException {
        JSch jsch = new JSch();
        
        // 私钥认证
        if (server.getPrivateKey() != null && !server.getPrivateKey().isEmpty()) {
            jsch.addIdentity("sftp_key", server.getPrivateKey().getBytes(), null, null);
        }
        
        Session session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
        
        // 密码认证
        if (server.getPassword() != null && !server.getPassword().isEmpty()) {
            session.setPassword(server.getPassword());
        }
        
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        
        Channel channel = session.openChannel("sftp");
        channel.connect();
        return (ChannelSftp) channel;
    }
    
    @Override
    public boolean testConnection(ServerConfig server) {
        try {
            ChannelSftp sftp = connect(server);
            sftp.disconnect();
            sftp.getSession().disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public List<FileResource> listFiles(ServerConfig server, String path) {
        // 使用 ChannelSftp.ls() 获取文件列表
        // Vector<LsEntry> -> List<FileResource>
    }
    
    @Override
    public InputStream downloadFile(ServerConfig server, String path) {
        // 使用 ChannelSftp.get() 获取 InputStream
    }
    
    // 其他方法实现
}
```

### 4.7 FtpService 实现

```java
package com.filemanager.service;

import org.apache.commons.net.ftp.*;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.io.IOException;

@Service
public class FtpService implements FileProtocolService {
    
    private FTPClient connect(ServerConfig server) throws IOException {
        FTPClient ftpClient = new FTPClient();
        ftpClient.connect(server.getHost(), server.getPort());
        ftpClient.login(server.getUsername(), server.getPassword());
        
        if (server.getPassiveMode() != null && server.getPassiveMode()) {
            ftpClient.enterLocalPassiveMode();
        }
        
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        return ftpClient;
    }
    
    @Override
    public boolean testConnection(ServerConfig server) {
        try {
            FTPClient ftpClient = connect(server);
            ftpClient.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public List<FileResource> listFiles(ServerConfig server, String path) {
        // 使用 FTPClient.listFiles() 或 listNames()
        // FTPFile[] -> List<FileResource>
    }
    
    @Override
    public InputStream downloadFile(ServerConfig server, String path) {
        // 使用 FTPClient.retrieveFileStream()
    }
    
    // 其他方法实现
}
```

### 4.8 FileService 改造

```java
@Service
public class FileService {
    
    @Autowired
    private Map<String, FileProtocolService> protocolServices;
    // Spring自动注入：webDavService, smbService, sftpService, ftpService
    
    private FileProtocolService getProtocolService(ServerConfig server) {
        String protocol = server.getProtocol().toLowerCase();
        String beanName = protocol.equals("webdav") ? "webDavService" : protocol + "Service";
        FileProtocolService service = protocolServices.get(beanName);
        if (service == null) {
            throw new ProtocolException(protocol, "init", "不支持的协议类型");
        }
        return service;
    }
    
    public List<FileDTO> getFileTree(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        List<FileResource> resources = service.listFiles(server, path);
        
        return resources.stream()
                .filter(r -> !r.getPath().equals(path))
                .map(r -> toDTO(r, serverId))
                .collect(Collectors.toList());
    }
    
    public InputStream downloadFile(Long serverId, String path) {
        ServerConfig server = getServer(serverId);
        FileProtocolService service = getProtocolService(server);
        return service.downloadFile(server, path);
    }
    
    // 其他方法类似改造
}
```

### 4.9 ProtocolException 统一异常

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
    
    public String getProtocol() { return protocol; }
    public String getOperation() { return operation; }
}
```

---

## 5. 前端设计

### 5.1 ServerConfig 实体扩展

前端对应扩展字段：

```javascript
// server配置对象
{
  id: Long,
  name: String,
  protocol: String,      // webdav/smb/sftp/ftp
  url: String,           // WebDAV专用
  host: String,          // SMB/SFTP/FTP主机
  port: Number,          // SMB/SFTP/FTP端口
  shareName: String,     // SMB共享名
  domain: String,        // SMB域
  username: String,
  password: String,
  privateKey: String,    // SFTP私钥
  passiveMode: Boolean,  // FTP被动模式
  rootPath: String,
  enabled: Boolean,
  description: String
}
```

### 5.2 ServerDialog.vue 动态表单

**表单字段配置：**

```javascript
const protocolFields = {
  webdav: [
    { key: 'url', label: '服务器地址', type: 'text', required: true, placeholder: 'https://nas.example.com/webdav' },
    { key: 'username', label: '用户名', type: 'text', required: true },
    { key: 'password', label: '密码', type: 'password', required: true },
    { key: 'rootPath', label: '根路径', type: 'text', default: '/' }
  ],
  smb: [
    { key: 'host', label: '主机地址', type: 'text', required: true, placeholder: '192.168.1.100' },
    { key: 'port', label: '端口', type: 'number', default: 445 },
    { key: 'shareName', label: '共享名', type: 'text', required: true, placeholder: 'share' },
    { key: 'domain', label: '域', type: 'text', placeholder: 'WORKGROUP' },
    { key: 'username', label: '用户名', type: 'text', required: true },
    { key: 'password', label: '密码', type: 'password', required: true },
    { key: 'rootPath', label: '根路径', type: 'text', default: '/' }
  ],
  sftp: [
    { key: 'host', label: '主机地址', type: 'text', required: true },
    { key: 'port', label: '端口', type: 'number', default: 22 },
    { key: 'username', label: '用户名', type: 'text', required: true },
    { key: 'authType', label: '认证方式', type: 'select', options: ['password', 'privateKey'], default: 'password' },
    { key: 'password', label: '密码', type: 'password', showIf: { authType: 'password' } },
    { key: 'privateKey', label: '私钥', type: 'textarea', rows: 5, showIf: { authType: 'privateKey' }, placeholder: '-----BEGIN RSA PRIVATE KEY-----\n...\n-----END RSA PRIVATE KEY-----' },
    { key: 'rootPath', label: '根路径', type: 'text', default: '/' }
  ],
  ftp: [
    { key: 'host', label: '主机地址', type: 'text', required: true },
    { key: 'port', label: '端口', type: 'number', default: 21 },
    { key: 'username', label: '用户名', type: 'text', required: true },
    { key: 'password', label: '密码', type: 'password', required: true },
    { key: 'passiveMode', label: '被动模式', type: 'checkbox', default: true, help: '防火墙环境下建议启用' },
    { key: 'rootPath', label: '根路径', type: 'text', default: '/' }
  ]
}
```

**动态表单渲染逻辑：**

```vue
<template>
  <el-form-item label="协议类型">
    <el-select v-model="form.protocol" @change="onProtocolChange">
      <el-option label="WebDAV" value="webdav" />
      <el-option label="SMB" value="smb" />
      <el-option label="SFTP" value="sftp" />
      <el-option label="FTP" value="ftp" />
    </el-select>
  </el-form-item>
  
  <!-- 动态渲染协议特定字段 -->
  <template v-for="field in currentFields" :key="field.key">
    <el-form-item 
      v-if="shouldShowField(field)"
      :label="field.label"
      :required="field.required"
    >
      <!-- text/password/number/select/textarea/checkbox 根据type渲染 -->
      <el-input v-if="field.type === 'text' || field.type === 'password'"
        v-model="form[field.key]"
        :type="field.type"
        :placeholder="field.placeholder"
      />
      <el-input-number v-if="field.type === 'number'"
        v-model="form[field.key]"
        :value="field.default"
      />
      <el-select v-if="field.type === 'select'"
        v-model="form[field.key]"
      >
        <el-option v-for="opt in field.options" :key="opt" :label="opt" :value="opt" />
      </el-select>
      <el-input v-if="field.type === 'textarea'"
        v-model="form[field.key]"
        type="textarea"
        :rows="field.rows || 3"
        :placeholder="field.placeholder"
      />
      <el-checkbox v-if="field.type === 'checkbox'"
        v-model="form[field.key]"
      >
        {{ field.help || '' }}
      </el-checkbox>
    </el-form-item>
  </template>
</template>

<script setup>
const currentFields = computed(() => {
  return protocolFields[form.protocol] || []
})

const shouldShowField = (field) => {
  if (!field.showIf) return true
  const condition = field.showIf
  for (const [key, value] of Object.entries(condition)) {
    if (form[key] !== value) return false
  }
  return true
}

const onProtocolChange = () => {
  // 清空非通用字段
  const commonFields = ['name', 'protocol', 'username', 'rootPath', 'description']
  for (const key in form) {
    if (!commonFields.includes(key)) {
      form[key] = null
    }
  }
  // 设置默认值
  currentFields.value.forEach(field => {
    if (field.default && form[field.key] === null) {
      form[field.key] = field.default
    }
  })
}
</script>
```

### 5.3 ServerManage.vue 改动

- 表格显示协议类型列
- 协议类型用不同颜色Tag标识
- 测试连接按钮逻辑不变

---

## 6. API设计

### 6.1 ServerController 扩展

现有API不变，新增返回字段：

```json
// GET /api/servers/{id} 返回
{
  "id": 1,
  "name": "NAS共享",
  "protocol": "smb",
  "host": "192.168.1.100",
  "port": 445,
  "shareName": "share",
  "domain": null,
  "username": "admin",
  "rootPath": "/",
  "enabled": true
}
```

### 6.2 测试连接

```json
// POST /api/servers/{id}/test
{
  "success": true,
  "message": "连接成功"
}
```

---

## 7. 安全考虑

### 7.1 密码/私钥加密存储

- 现有 PasswordEncryptor 继续用于密码加密
- privateKey 字段也使用同样加密方式
- 加密密钥从环境变量读取

### 7.2 连接超时

各协议设置合理超时时间：
- SMB: 30秒
- SFTP: 30秒
- FTP: 30秒

### 7.3 连接池

暂不实现连接池，每次操作建立新连接后立即关闭。
后续优化可考虑连接池管理。

---

## 8. 测试要点

### 8.1 单元测试

- 各ProtocolService的连接测试
- 文件操作测试（mock或真实服务器）

### 8.2 集成测试

- 前端表单动态渲染
- 各协议完整流程测试

### 8.3 测试环境

需要准备测试服务器：
- SMB: Windows共享或NAS
- SFTP: Linux服务器
- FTP: FTP服务器

---

## 9. 实施顺序

1. **Phase 1: 后端基础设施**
   - 添加Maven依赖
   - 创建FileProtocolService接口
   - 创建FileResource类
   - 创建ProtocolException异常类
   - 执行数据库迁移

2. **Phase 2: 协议实现**
   - 改造WebDavService实现接口
   - 实现SmbService
   - 实现SftpService
   - 实现FtpService

3. **Phase 3: FileService改造**
   - 添加协议服务选择逻辑
   - 改造各方法使用FileProtocolService

4. **Phase 4: 前端改造**
   - 扩展ServerDialog动态表单
   - 改造ServerManage显示协议类型

5. **Phase 5: 测试验证**
   - 各协议连接测试
   - 完整功能测试

---

## 10. 后续扩展

- **Rsync备份模块** - 独立设计，定时同步任务
- **连接池优化** - 提升性能
- **协议扩展** - NFS等其他协议支持