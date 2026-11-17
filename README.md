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

### 已知事项

- **登录页「记住我」的密码以明文存于 localStorage**（键名
  `file-manager-remembered-login`），同源脚本（XSS）可读取。
  这是浏览器端「记住密码」的通行做法，仅在用户主动勾选时写入。
  若需在多人共用设备上使用，建议改为只记用户名，或由后端签发长期
  令牌替代。当前保留现状，暂不调整。

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

## 本机目录共享（可选）

想把本机某个目录当作 WebDAV 服务器挂进应用（例如让「服务器」列表里指向
`127.0.0.1:8181` 的配置可用），可用 [WsgiDAV](https://github.com/mar10/wsgidav)。

脚本位于 `scripts/dev-webdav/`，共享目录与账号在 `wsgidav.yaml` 里配置。
虚拟环境自动创建在 `~/.file-manager/webdav/.venv`（不入库）。

### 日常启停

```bash
./scripts/dev-webdav/start.sh                # 共享 ~/Downloads
./scripts/dev-webdav/start.sh ~/Documents    # 换共享目录(会重启实例)
./scripts/dev-webdav/stop.sh
```

首次运行会自动创建虚拟环境并安装 `wsgidav` + `cheroot`。

默认监听 `127.0.0.1:8181`（仅本机可访问），账号 `admin` / `admin`，
共享目录 `~/Downloads`；改这两项编辑 `scripts/dev-webdav/wsgidav.yaml`。

### 应用内配置对应关系

| 应用里的字段 | 值 |
| --- | --- |
| 协议 | WebDAV |
| 地址 | `http://127.0.0.1:8181` |
| 用户名 / 密码 | `admin` / `admin` |
| 根路径 | `/` |

### 其他可选方案

- **Docker**：`docker run -d --name webdav -p 8181:80 -v ~/Downloads:/var/www -e USERNAME=admin -e PASSWORD=admin bytemark/webdav`
- **rclone**：`rclone serve webdav ~/Downloads --addr 127.0.0.1:8181 --user admin --pass admin`
- **macOS 自带 Apache**：系统仍带 `mod_dav`，但需要改 `/etc/apache2/httpd.conf` 并重启服务，配置较重，不推荐

> 该目录为**可读写**共享，应用内的删除/重命名会直接作用于本机文件。
> 建议专门建一个共享目录，而不是直接挂 `~/Downloads`。
>
> 若要让局域网其他设备访问，需把 `host` 改成 `0.0.0.0` 并放行防火墙；
> 暴露到局域网时请务必改掉默认口令。

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