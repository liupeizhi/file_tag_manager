# 文件标签浏览器

一个基于WebDAV协议的多服务器文件管理系统，支持文件浏览、上传下载、预览、搜索排序等功能。

## 技术栈

**后端:**
- Spring Boot 3.2.0
- Spring Data JPA
- MySQL 8.x
- Sardine WebDAV Client

**前端:**
- Vue 3
- Element Plus
- Pinia
- Vite

## 项目结构

```
file-manager/
├── file-manager-backend/      # 后端项目
│   ├── src/main/java/com/filemanager/
│   │   ├── config/           # 配置类
│   │   ├── controller/       # 控制器层
│   │   ├── dto/             # 数据传输对象
│   │   ├── entity/          # 实体类
│   │   ├── exception/       # 异常处理
│   │   ├── repository/      # 数据访问层
│   │   └── service/         # 服务层
│   └── pom.xml
│
├── file-manager-frontend/     # 前端项目
│   ├── src/
│   │   ├── api/             # API封装
│   │   ├── assets/          # 静态资源
│   │   ├── components/      # Vue组件
│   │   ├── store/           # Pinia状态管理
│   │   ├── utils/           # 工具函数
│   │   └── views/           # 页面
│   ├── package.json
│   └── vite.config.js
│
└── docs/                      # 文档
    └── superpowers/
        ├── specs/             # 设计文档
        └── plans/             # 实现计划
```

## 快速开始

### 前置要求

- Java 17+
- Maven 3.x
- MySQL 8.x
- Node.js 16+ 或 Bun

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS file_manager 
DEFAULT CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;
```

### 2. 配置数据库连接

编辑 `file-manager-backend/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/file_manager?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
    username: root
    password: your_password
```

### 3. 启动后端

```bash
cd file-manager-backend
mvn spring-boot:run
```

后端服务将运行在 http://localhost:8080

### 4. 启动前端

```bash
cd file-manager-frontend
bun install  # 或 npm install
bun run dev  # 或 npm run dev
```

前端应用将运行在 http://localhost:3000

## 功能特性

### 已实现功能

✅ WebDAV服务器连接管理  
✅ 多服务器同时连接  
✅ 左侧文件树 + 右侧文件列表布局  
✅ 文件类型图标展示  
✅ 文件快速预览（文本、图片、视频、音频）  
✅ 文件上传（拖拽、多文件、进度显示）  
✅ 文件下载  
✅ 文件重命名  
✅ 文件删除  
✅ 文件夹创建  
✅ 文件名过滤  
✅ 文件类型过滤  
✅ 修改时间范围过滤  
✅ 多字段排序  
✅ 分页加载  
✅ 文件元数据缓存  
✅ 密码加密存储  

### 后续扩展

- 文件标签管理
- 文件收藏功能
- 文件分享链接
- 批量操作
- 文件版本管理
- 缩略图预览
- 深色模式
- 移动端适配

## API文档

### 服务器管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/servers | 获取服务器列表 |
| POST | /api/servers | 添加服务器 |
| PUT | /api/servers/{id} | 更新服务器配置 |
| DELETE | /api/servers/{id} | 删除服务器 |
| POST | /api/servers/{id}/test | 测试服务器连接 |

### 文件操作

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/files/tree | 获取文件树 |
| GET | /api/files | 获取文件列表 |
| POST | /api/files/upload | 上传文件 |
| GET | /api/files/{id}/download | 下载文件 |
| PUT | /api/files/{id} | 重命名文件 |
| DELETE | /api/files/{id} | 删除文件 |
| POST | /api/files/create-folder | 创建文件夹 |

### 文件预览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/preview/{id}/text | 预览文本文件 |
| GET | /api/preview/{id}/image | 预览图片 |
| GET | /api/preview/{id}/video | 预览视频 |
| GET | /api/preview/{id}/audio | 预览音频 |

## 支持的WebDAV服务器

- 群晖 NAS
- 威联通 NAS
- Nextcloud
- 坚果云
- 百度网盘
- InfiniCLOUD
- 其他标准WebDAV服务器

## 许可证

MIT License

## 作者

文件标签浏览器开发团队