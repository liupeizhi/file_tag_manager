# 文件标签浏览器 - 设计文档

**项目名称:** 文件标签浏览器  
**创建日期:** 2026-03-26  
**技术栈:** Spring Boot 3 + Vue 3 + MySQL  
**部署模式:** 本地部署，个人使用

---

## 1. 项目概述

### 1.1 项目目标

构建一个基于WebDAV协议的多服务器文件管理系统，支持：
- 管理多个WebDAV服务器连接
- 浏览、上传、下载、重命名、删除文件
- 文件快速预览（文本、图片、视频、音频）
- 文件搜索、过滤和排序功能

### 1.2 目标用户

个人用户，需要管理多个WebDAV服务器（如群晖NAS、威联通、坚果云、百度网盘等）上的文件。

### 1.3 核心价值

- **统一管理**: 一个界面管理多个WebDAV服务器
- **便捷操作**: 类似本地文件管理器的操作体验
- **快速预览**: 无需下载即可预览常见文件类型
- **高效查找**: 支持多维度搜索和排序

---

## 2. 整体架构

### 2.1 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        前端层 (Vue 3)                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │服务器管理│  │文件树导航│  │文件列表  │  │文件预览  │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│  │文件上传  │  │搜索过滤  │  │排序功能  │                 │
│  └──────────┘  └──────────┘  └──────────┘                 │
└─────────────────────────────────────────────────────────────┘
                              ↓ REST API
┌─────────────────────────────────────────────────────────────┐
│                     后端层 (Spring Boot 3)                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                    Controller 层                      │  │
│  │  ServerController | FileController | PreviewController│  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                    Service 层                         │  │
│  │  ServerService | FileService | WebDavService         │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                    Repository 层                      │  │
│  │  ServerRepository | FileMetaRepository               │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────┬─────────────────────────────────────┐
│    数据库 (MySQL)     │     外部服务 (WebDAV)               │
│  ┌────────────────┐  │  ┌──────────────────────────────┐   │
│  │server_config   │  │  │  WebDAV Server 1 (NAS)       │   │
│  │file_metadata   │  │  ├──────────────────────────────┤   │
│  │(缓存)          │  │  │  WebDAV Server 2 (云存储)    │   │
│  └────────────────┘  │  └──────────────────────────────┘   │
└──────────────────────┴─────────────────────────────────────┘
```

### 2.2 架构说明

- **前后端分离**: 前端Vue 3应用通过REST API与后端Spring Boot通信
- **WebDAV代理**: 后端作为WebDAV客户端，代理所有文件操作
- **数据缓存**: 数据库存储服务器配置和文件元数据，减少WebDAV请求
- **多服务器支持**: 可同时连接多个WebDAV服务器

---

## 3. 数据库设计

### 3.1 表结构

#### server_config (服务器配置表)

```sql
CREATE TABLE server_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '服务器名称',
    url VARCHAR(500) NOT NULL COMMENT 'WebDAV服务器地址',
    username VARCHAR(100) COMMENT '用户名',
    password VARCHAR(255) COMMENT '密码(加密存储)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name)
);
```

#### file_metadata (文件元数据缓存表)

```sql
CREATE TABLE file_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    server_id BIGINT NOT NULL COMMENT '所属服务器ID',
    path VARCHAR(1000) NOT NULL COMMENT '文件路径',
    name VARCHAR(255) NOT NULL COMMENT '文件名',
    is_directory BOOLEAN DEFAULT FALSE COMMENT '是否为目录',
    size BIGINT COMMENT '文件大小(字节)',
    content_type VARCHAR(100) COMMENT '文件MIME类型',
    last_modified TIMESTAMP COMMENT '最后修改时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (server_id) REFERENCES server_config(id) ON DELETE CASCADE,
    INDEX idx_server_path (server_id, path),
    INDEX idx_name (name),
    INDEX idx_content_type (content_type),
    INDEX idx_last_modified (last_modified)
);
```

#### file_tag (文件标签表)

```sql
CREATE TABLE file_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_id BIGINT NOT NULL COMMENT '文件元数据ID',
    tag_name VARCHAR(50) NOT NULL COMMENT '标签名',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES file_metadata(id) ON DELETE CASCADE,
    INDEX idx_tag_name (tag_name)
);
```

### 3.2 设计要点

- **密码安全**: password字段使用AES加密存储
- **缓存机制**: file_metadata表缓存文件信息，减少WebDAV请求次数
- **索引优化**: 针对常用查询条件（文件名、类型、时间）建立索引
- **级联删除**: 删除服务器时自动删除相关文件元数据和标签

---

## 4. 后端设计

### 4.1 项目结构

```
file-manager-backend/
├── src/main/java/com/filemanager/
│   ├── FileManagerApplication.java
│   ├── config/                    # 配置类
│   │   ├── CorsConfig.java       # 跨域配置
│   │   ├── WebDavConfig.java     # WebDAV配置
│   │   └── SecurityConfig.java   # 安全配置
│   ├── controller/               # 控制器层
│   │   ├── ServerController.java # 服务器管理
│   │   ├── FileController.java   # 文件操作
│   │   └── PreviewController.java # 文件预览
│   ├── service/                  # 服务层
│   │   ├── ServerService.java
│   │   ├── FileService.java
│   │   └── WebDavService.java    # WebDAV客户端封装
│   ├── repository/               # 数据访问层
│   │   ├── ServerConfigRepository.java
│   │   └── FileMetadataRepository.java
│   ├── entity/                   # 实体类
│   │   ├── ServerConfig.java
│   │   └── FileMetadata.java
│   ├── dto/                      # 数据传输对象
│   │   ├── ServerConfigDTO.java
│   │   ├── FileDTO.java
│   │   └── ApiResponse.java
│   └── exception/                # 异常处理
│       ├── GlobalExceptionHandler.java
│       └── WebDavException.java
├── src/main/resources/
│   ├── application.yml
│   └── application-dev.yml
└── pom.xml
```

### 4.2 核心API设计

#### 服务器管理API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/servers | 添加服务器 |
| GET | /api/servers | 获取服务器列表 |
| GET | /api/servers/{id} | 获取服务器详情 |
| PUT | /api/servers/{id} | 更新服务器配置 |
| DELETE | /api/servers/{id} | 删除服务器 |
| POST | /api/servers/{id}/test | 测试服务器连接 |

#### 文件操作API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/files | 获取文件列表(支持分页、过滤、排序) |
| GET | /api/files/tree | 获取文件树结构 |
| GET | /api/files/{id} | 获取文件详情 |
| GET | /api/files/{id}/download | 下载文件 |
| POST | /api/files/upload | 上传文件 |
| PUT | /api/files/{id} | 重命名文件 |
| DELETE | /api/files/{id} | 删除文件 |
| POST | /api/files/create-folder | 创建文件夹 |

#### 文件预览API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/preview/{id}/text | 预览文本文件 |
| GET | /api/preview/{id}/image | 预览图片 |
| GET | /api/preview/{id}/video | 预览视频 |
| GET | /api/preview/{id}/audio | 预览音频 |

### 4.3 WebDAV客户端实现

使用 **Sardine** 库作为WebDAV客户端，核心方法：

```java
public class WebDavService {
    private Sardine sardine;
    
    // 初始化连接
    public void connect(String url, String username, String password);
    
    // 列出目录内容
    public List<DavResource> list(String path);
    
    // 下载文件
    public InputStream getFile(String path);
    
    // 上传文件
    public void put(String path, InputStream data);
    
    // 创建目录
    public void createDirectory(String path);
    
    // 删除文件/目录
    public void delete(String path);
    
    // 移动/重命名
    public void move(String from, String to);
}
```

---

## 5. 前端设计

### 5.1 项目结构

```
file-manager-frontend/
├── public/
│   └── favicon.ico
├── src/
│   ├── main.js                   # 应用入口
│   ├── App.vue                   # 根组件
│   ├── assets/                   # 静态资源
│   │   ├── icons/               # 文件类型图标
│   │   └── styles/              # 全局样式
│   ├── components/              # 组件
│   │   ├── layout/              # 布局组件
│   │   │   ├── Header.vue       # 顶部导航
│   │   │   └── MainLayout.vue   # 主布局
│   │   ├── server/              # 服务器管理
│   │   │   ├── ServerList.vue   # 服务器列表
│   │   │   └── ServerDialog.vue # 服务器配置弹窗
│   │   ├── file/                # 文件操作
│   │   │   ├── FileTree.vue     # 文件树
│   │   │   ├── FileList.vue     # 文件列表
│   │   │   ├── FileUpload.vue   # 文件上传
│   │   │   └── FileActions.vue  # 文件操作按钮
│   │   ├── preview/             # 文件预览
│   │   │   ├── TextPreview.vue
│   │   │   ├── ImagePreview.vue
│   │   │   ├── VideoPreview.vue
│   │   │   └── AudioPreview.vue
│   │   └── common/              # 公共组件
│   │       ├── SearchFilter.vue # 搜索过滤
│   │       └── SortControl.vue  # 排序控制
│   ├── views/                   # 页面
│   │   └── Home.vue             # 主页面
│   ├── api/                     # API调用
│   │   ├── server.js
│   │   ├── file.js
│   │   └── preview.js
│   ├── store/                   # 状态管理(Pinia)
│   │   ├── index.js
│   │   ├── server.js
│   │   └── file.js
│   └── utils/                   # 工具函数
│       ├── file-type.js         # 文件类型判断
│       └── date-format.js       # 日期格式化
├── package.json
├── vite.config.js
└── index.html
```

### 5.2 UI布局

```
┌─────────────────────────────────────────────────────────┐
│  Header (服务器选择器、搜索、设置)                      │
├──────────────┬──────────────────────────────────────────┤
│              │                                          │
│  FileTree    │         FileList (文件列表)             │
│  (文件树)    │                                          │
│              │  ┌────────────────────────────────────┐ │
│  ├─ 文档     │  │ 图标 | 名称 | 大小 | 类型 | 修改时间│ │
│  │  └─ 工作  │  ├────────────────────────────────────┤ │
│  ├─ 图片     │  │ 📄  | report.pdf | 2MB | PDF | 昨天 │ │
│  │  └─ 照片  │  │ 📁  | 项目文件夹 | - | 文件夹 | 今天 │ │
│  └─ 视频     │  └────────────────────────────────────┘ │
│              │                                          │
└──────────────┴──────────────────────────────────────────┘
```

### 5.3 状态管理

```javascript
// 服务器状态
currentServer: {
  id: null,
  name: '',
  url: ''
}

// 文件树状态
fileTree: {
  expandedKeys: [],
  currentNode: null,
  treeData: []
}

// 文件列表状态
fileList: {
  data: [],
  loading: false,
  pagination: {
    page: 1,
    pageSize: 50,
    total: 0
  },
  filters: {
    name: '',
    type: '',
    startDate: '',
    endDate: ''
  },
  sort: {
    field: 'name',
    order: 'asc'
  }
}
```

---

## 6. 文件类型识别与图标

### 6.1 文件类型映射

| 类别 | 扩展名 | 图标 | 颜色 |
|------|--------|------|------|
| 文档 | pdf, doc, docx, xls, xlsx, ppt, pptx, txt, md | 特定图标 | 各异 |
| 图片 | jpg, jpeg, png, gif, svg | image | #9B59B6 |
| 视频 | mp4, avi, mkv, mov | video | #E91E63 |
| 音频 | mp3, wav, flac | audio | #00BCD4 |
| 压缩 | zip, rar, 7z | archive | #FF9800 |
| 代码 | js, ts, java, py, html, css | code | 各异 |
| 文件夹 | - | folder | #FFA726 |
| 默认 | - | file | #95A5A6 |

### 6.2 实现方式

使用Element Plus Icon或Font Awesome图标库，根据文件扩展名自动匹配图标和颜色。

---

## 7. 文件预览功能

### 7.1 支持的预览类型

| 文件类型 | 实现方案 | 限制 |
|----------|----------|------|
| 文本(txt, md, code) | vue-highlight.js | 最大5MB |
| 图片(jpg, png, gif) | el-image-viewer | 无 |
| 视频(mp4, webm) | HTML5 video | 格式支持有限 |
| 音频(mp3, wav) | HTML5 audio | 格式支持有限 |

### 7.2 预览弹窗

- 统一的弹窗布局
- 显示文件基本信息（名称、类型、大小）
- 提供操作按钮（下载、重命名、删除）
- 预览失败时提示下载查看

---

## 8. 文件上传下载

### 8.1 上传功能

**特性:**
- 拖拽上传 + 点击上传
- 多文件同时上传
- 上传进度显示
- 失败重试
- 文件大小限制: 100MB

**实现:**
- 前端: FormData + axios + 进度监听
- 后端: MultipartFile + WebDAV put操作

### 8.2 下载功能

**特性:**
- 流式传输，避免内存溢出
- 支持大文件下载
- 支持断点续传（Range请求）

**实现:**
- 后端从WebDAV获取流，转发给前端
- 设置正确的Content-Disposition头

---

## 9. 搜索过滤和排序

### 9.1 过滤条件

- **文件名**: 模糊匹配
- **文件类型**: 下拉选择（文档、图片、视频、音频等）
- **修改时间**: 日期范围选择

### 9.2 排序字段

- 文件名
- 文件大小
- 文件类型
- 修改时间
- 创建时间

### 9.3 实现方式

- 后端: JPA Specification动态查询
- 前端: Element Plus表格组件内置排序功能

---

## 10. 安全性设计

### 10.1 密码加密

- WebDAV密码使用AES加密存储
- 加密密钥配置在application.yml中

### 10.2 API安全

- 输入验证和过滤
- SQL注入防护
- XSS防护
- 操作日志记录

---

## 11. 性能优化

### 11.1 文件树懒加载

- 只加载当前展开的文件夹
- 按需加载子节点

### 11.2 元数据缓存

- 后端缓存文件列表到数据库
- 缓存过期时间: 5分钟
- 支持手动刷新

### 11.3 分页加载

- 文件列表默认每页50条
- 支持滚动加载
- 虚拟滚动优化大列表

---

## 12. 部署方案

### 12.1 开发环境

```bash
# 后端
cd file-manager-backend
mvn spring-boot:run

# 前端
cd file-manager-frontend
npm run dev
```

### 12.2 生产环境

**打包:**
```bash
# 后端
mvn clean package -DskipTests

# 前端
npm run build
```

**部署:**
1. 后端JAR包直接运行
2. 前端静态文件部署到Nginx或由Spring Boot提供
3. MySQL数据库独立部署

**启动脚本:**
```bash
#!/bin/bash
java -jar file-manager-backend.jar \
  --spring.profiles.active=prod \
  --server.port=8080 \
  --spring.datasource.url=jdbc:mysql://localhost:3306/file_manager
```

---

## 13. 技术栈总结

| 层次 | 技术选型 | 版本 |
|------|----------|------|
| 前端框架 | Vue 3 | 3.x |
| UI组件库 | Element Plus | 2.x |
| 状态管理 | Pinia | 2.x |
| 构建工具 | Vite | 5.x |
| 后端框架 | Spring Boot | 3.x |
| 数据访问 | Spring Data JPA | 3.x |
| WebDAV客户端 | Sardine | 5.x |
| 数据库 | MySQL | 8.x |
| 构建工具 | Maven | 3.x |

---

## 14. 功能清单

✅ WebDAV服务器连接和管理  
✅ 多服务器同时连接  
✅ 左侧文件树 + 右侧文件列表布局  
✅ 文件类型图标展示  
✅ 文件快速预览（文本、图片、视频、音频）  
✅ 文件上传（拖拽、多文件、进度显示）  
✅ 文件下载（流式传输、断点续传）  
✅ 文件重命名  
✅ 文件删除  
✅ 文件夹创建  
✅ 文件名过滤  
✅ 文件类型过滤  
✅ 修改时间范围过滤  
✅ 多字段排序  
✅ 分页加载  
✅ 文件元数据缓存  
✅ 操作日志记录  
✅ 密码加密存储  

---

## 15. 后续扩展

- 文件标签管理
- 文件收藏功能
- 文件分享链接
- 批量操作
- 文件版本管理
- 缩略图预览
- 深色模式
- 移动端适配