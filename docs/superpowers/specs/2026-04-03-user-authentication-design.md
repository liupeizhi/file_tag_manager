# 用户登录功能设计文档

## 1. 需求概述

为文件管理系统添加用户登录功能，支持管理员和普通用户两种角色，实现安全的身份认证和权限控制。

### 核心需求
- **账号创建**：混合模式（管理员创建 + 用户注册需审核）
- **认证方式**：Session Cookie
- **登录有效期**：默认7天，支持"记住我"延长至30天
- **角色区分**：管理员（ADMIN）和普通用户（USER）

## 2. 权限体系

### 角色定义

#### 管理员 (ADMIN)
- 访问后台管理页面
- 管理服务器配置
- 管理用户账号（创建、审核、禁用、删除）
- 管理文件标签
- 访问文件浏览器

#### 普通用户 (USER)
- 访问文件浏览器
- 文件预览
- 给文件打标签
- 管理个人资料

### 权限控制矩阵

| 功能模块 | 游客 | 普通用户 | 管理员 |
|---------|------|---------|--------|
| 登录/注册 | ✓ | ✓ | ✓ |
| 文件浏览 | ✗ | ✓ | ✓ |
| 文件预览 | ✗ | ✓ | ✓ |
| 文件标签 | ✗ | ✓ | ✓ |
| 服务器管理 | ✗ | ✗ | ✓ |
| 用户管理 | ✗ | ✗ | ✓ |

## 3. 数据库设计

### 用户表 (user)

```sql
CREATE TABLE user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL COMMENT '登录名',
  password VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
  email VARCHAR(100) UNIQUE COMMENT '邮箱',
  nickname VARCHAR(50) COMMENT '昵称',
  role ENUM('ADMIN', 'USER') DEFAULT 'USER' COMMENT '角色',
  status ENUM('ACTIVE', 'PENDING', 'DISABLED') DEFAULT 'PENDING' COMMENT '状态',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_username (username),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

### 状态说明
- `ACTIVE`: 已激活，可正常使用
- `PENDING`: 待审核（用户注册后默认状态）
- `DISABLED`: 已禁用

### 初始管理员账号
```sql
-- 默认管理员账号
-- 用户名: admin
-- 密码: admin123 (BCrypt加密后)
INSERT INTO user (username, password, email, nickname, role, status)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E', 'admin@example.com', '系统管理员', 'ADMIN', 'ACTIVE');
```

## 4. 后端实现

### 4.1 新增依赖

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### 4.2 核心类结构

#### 实体层
- `User.java` - 用户实体类
- `Role.java` - 角色枚举
- `UserStatus.java` - 用户状态枚举

#### 数据访问层
- `UserRepository.java` - 用户数据访问接口

#### 业务逻辑层
- `UserService.java` - 用户业务逻辑
- `AuthService.java` - 认证业务逻辑

#### 控制器层
- `AuthController.java` - 认证API
- `UserController.java` - 用户个人信息API
- `AdminUserController.java` - 管理员用户管理API

#### 安全配置
- `SecurityConfig.java` - Spring Security配置
- `CustomUserDetailsService.java` - 用户详情服务实现
- `AdminInterceptor.java` - 管理员权限拦截器

#### DTO
- `LoginRequest.java` - 登录请求
- `RegisterRequest.java` - 注册请求
- `UserDTO.java` - 用户信息响应
- `UpdateUserRequest.java` - 更新用户请求

### 4.3 API设计

#### 认证API (无需登录)
```
POST   /api/auth/login          - 登录
  请求: { username, password, rememberMe }
  响应: { id, username, email, nickname, role, status }

POST   /api/auth/register       - 注册
  请求: { username, password, email, nickname }
  响应: { message: "注册成功，请等待管理员审核" }

POST   /api/auth/logout         - 登出
  响应: { message: "登出成功" }

GET    /api/auth/me             - 获取当前用户信息
  响应: { id, username, email, nickname, role, status }
```

#### 用户API (需登录)
```
GET    /api/user/profile        - 获取个人资料
PUT    /api/user/profile        - 更新个人资料
  请求: { nickname, email }
PUT    /api/user/password       - 修改密码
  请求: { oldPassword, newPassword }
```

#### 管理员API (仅管理员)
```
GET    /api/admin/users                 - 用户列表
  参数: page, size, status, role
  响应: { content: [...], totalElements, totalPages }

POST   /api/admin/users                 - 创建用户
  请求: { username, password, email, nickname, role }
  
PUT    /api/admin/users/{id}            - 更新用户
  请求: { email, nickname, role }

PUT    /api/admin/users/{id}/approve    - 审核通过
PUT    /api/admin/users/{id}/disable    - 禁用用户
PUT    /api/admin/users/{id}/enable     - 启用用户
DELETE /api/admin/users/{id}            - 删除用户
```

### 4.4 Spring Security配置要点

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // 前后端分离，禁用CSRF
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)
            );
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 4.5 Session配置

```yaml
# application.yml
server:
  servlet:
    session:
      timeout: 7d  # 默认7天
      cookie:
        http-only: true
        same-site: lax
```

## 5. 前端实现

### 5.1 目录结构

```
src/
├── views/
│   ├── Login.vue              # 登录页
│   ├── Register.vue           # 注册页
│   ├── AdminLayout.vue        # 管理后台布局
│   └── admin/
│       ├── ServerManage.vue   # 服务器管理（已存在）
│       └── UserManage.vue     # 用户管理
├── store/
│   └── user.js                # 用户状态管理
├── router/
│   └── index.js               # 路由配置（更新）
└── api/
    └── auth.js                # 认证API
```

### 5.2 路由配置

```javascript
const routes = [
  {
    path: '/login',
    component: Login,
    meta: { guest: true }
  },
  {
    path: '/register',
    component: Register,
    meta: { guest: true }
  },
  {
    path: '/',
    component: MainLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', component: Home },
      { path: 'files', component: FileList }
    ]
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: 'servers', component: ServerManage },
      { path: 'users', component: UserManage }
    ]
  }
]

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const isLoggedIn = userStore.isLoggedIn
  const isAdmin = userStore.isAdmin
  
  // 需要登录但未登录
  if (to.meta.requiresAuth && !isLoggedIn) {
    next('/login')
  }
  // 需要管理员权限但不是管理员
  else if (to.meta.requiresAdmin && !isAdmin) {
    next('/403')
  }
  // 已登录访问登录/注册页
  else if (to.meta.guest && isLoggedIn) {
    next('/')
  }
  else {
    next()
  }
})
```

### 5.3 用户状态管理

```javascript
// store/user.js
export const useUserStore = defineStore('user', {
  state: () => ({
    user: null,
    token: null
  }),
  
  getters: {
    isLoggedIn: (state) => !!state.user,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    username: (state) => state.user?.username || '',
    nickname: (state) => state.user?.nickname || state.user?.username || ''
  },
  
  actions: {
    async login(credentials) {
      const response = await authApi.login(credentials)
      this.user = response.data
    },
    
    async logout() {
      await authApi.logout()
      this.user = null
    },
    
    async fetchCurrentUser() {
      try {
        const response = await authApi.me()
        this.user = response.data
      } catch (error) {
        this.user = null
      }
    }
  }
})
```

### 5.4 UI组件

#### 登录页
- 用户名输入框
- 密码输入框
- "记住我"复选框
- 登录按钮
- 注册链接

#### 注册页
- 用户名输入框
- 密码输入框
- 确认密码输入框
- 邮箱输入框
- 昵称输入框
- 注册按钮
- 登录链接

#### 用户管理页（管理员）
- 用户列表表格
  - 用户名、邮箱、昵称、角色、状态、创建时间
  - 操作按钮：审核、禁用/启用、编辑、删除
- 筛选器：按状态、按角色
- 创建用户按钮
- 分页组件

## 6. 安全措施

### 6.1 密码安全
- **加密算法**: BCrypt (强度10)
- **传输**: HTTPS + POST请求
- **存储**: 仅存储加密后的密码
- **验证**: 前端+后端双重验证

#### 密码规则
- 最小长度: 6字符
- 最大长度: 20字符
- 建议包含: 字母、数字、特殊字符

### 6.2 Session安全
- **HttpOnly Cookie**: 防止XSS攻击
- **SameSite**: 防止CSRF攻击
- **Session超时**: 7天（默认）或30天（记住我）
- **Session固定攻击防护**: 登录后重新生成Session ID

### 6.3 权限验证
- **后端**: Spring Security拦截器 + 自定义管理员拦截器
- **前端**: 路由守卫 + 组件级权限控制
- **双重验证**: 前后端都进行权限检查

### 6.4 输入验证
- **用户名**: 3-20字符，仅字母数字下划线
- **密码**: 6-20字符
- **邮箱**: 标准邮箱格式
- **昵称**: 2-20字符

### 6.5 防护措施
- SQL注入防护: JPA参数化查询
- XSS防护: 前端输入过滤，后端输出编码
- 暴力破解防护: 登录失败次数限制（可选）
- 敏感操作日志: 记录用户登录、权限变更等操作

## 7. 实施步骤

### 阶段一: 后端基础架构
1. 添加Spring Security依赖
2. 创建User实体和相关枚举
3. 创建UserRepository
4. 实现密码加密配置

### 阶段二: 后端认证功能
1. 实现CustomUserDetailsService
2. 配置Spring Security
3. 实现AuthController（登录、注册、登出）
4. 实现UserService

### 阶段三: 后端用户管理
1. 实现UserController（个人资料管理）
2. 实现AdminUserController（用户管理）
3. 添加管理员权限拦截器

### 阶段四: 前端登录注册
1. 创建登录页和注册页
2. 实现用户状态管理（store/user.js）
3. 实现认证API（api/auth.js）
4. 配置路由守卫

### 阶段五: 前端用户管理
1. 创建AdminLayout组件
2. 创建UserManage组件
3. 实现用户管理API调用
4. 完善权限控制UI

### 阶段六: 测试与优化
1. 功能测试
2. 安全测试
3. 性能优化
4. 文档完善

## 8. 测试计划

### 8.1 功能测试
- [ ] 用户注册（待审核状态）
- [ ] 管理员创建用户
- [ ] 用户登录（正确/错误密码）
- [ ] 记住我功能
- [ ] Session超时
- [ ] 用户登出
- [ ] 管理员审核用户
- [ ] 管理员禁用/启用用户
- [ ] 管理员删除用户
- [ ] 权限控制（普通用户访问管理页面）
- [ ] 路由守卫

### 8.2 安全测试
- [ ] SQL注入测试
- [ ] XSS攻击测试
- [ ] CSRF防护测试
- [ ] Session劫持测试
- [ ] 权限绕过测试

### 8.3 性能测试
- [ ] 登录响应时间
- [ ] 用户列表查询性能
- [ ] 并发登录测试

## 9. 后续优化建议

1. **登录失败次数限制**: 防止暴力破解
2. **密码修改提醒**: 定期提醒用户修改密码
3. **多因素认证**: 增强安全性
4. **用户操作日志**: 记录敏感操作
5. **在线用户管理**: 查看和强制下线在线用户
6. **用户分组**: 支持更细粒度的权限控制
7. **第三方登录**: 支持OAuth2.0登录

## 10. 注意事项

1. **默认管理员密码**: 首次部署后需立即修改默认密码
2. **Session存储**: 当前使用内存存储，生产环境建议使用Redis
3. **跨域配置**: 前后端分离部署需配置CORS
4. **HTTPS**: 生产环境必须使用HTTPS
5. **数据库备份**: 定期备份用户数据