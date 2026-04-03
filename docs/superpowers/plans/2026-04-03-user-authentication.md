# 用户认证功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为文件管理系统添加完整的用户登录、注册、权限控制功能，支持管理员和普通用户两种角色。

**Architecture:** 使用Spring Security + Session Cookie实现认证，前后端分离架构。后端提供RESTful API，前端使用Vue 3 + Element Plus实现登录注册页面和管理功能。通过路由守卫和拦截器实现权限控制。

**Tech Stack:** 
- 后端: Spring Boot 3, Spring Security, Spring Session, BCrypt, JPA
- 前端: Vue 3, Pinia, Vue Router, Element Plus, Axios
- 数据库: MySQL 8

---

## Phase 1: 后端基础架构

### Task 1: 添加Spring Security依赖

**Files:**
- Modify: `file-manager-backend/pom.xml`

- [ ] **Step 1: 在pom.xml中添加Spring Security依赖**

在 `<dependencies>` 标签内添加：

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

位置：在 `spring-boot-starter-data-jpa` 依赖之后

- [ ] **Step 2: 验证依赖添加成功**

Run: `cd file-manager-backend && mvn dependency:tree | grep spring-security`
Expected: 显示 spring-boot-starter-security 依赖树

- [ ] **Step 3: Commit**

```bash
git add file-manager-backend/pom.xml
git commit -m "feat: add Spring Security dependency for user authentication"
```

### Task 2: 创建用户实体和枚举类

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/entity/User.java`
- Create: `file-manager-backend/src/main/java/com/filemanager/enums/Role.java`
- Create: `file-manager-backend/src/main/java/com/filemanager/enums/UserStatus.java`

- [ ] **Step 1: 创建Role枚举**

```java
package com.filemanager.enums;

public enum Role {
    ADMIN,
    USER
}
```

- [ ] **Step 2: 创建UserStatus枚举**

```java
package com.filemanager.enums;

public enum UserStatus {
    ACTIVE,
    PENDING,
    DISABLED
}
```

- [ ] **Step 3: 创建User实体类**

```java
package com.filemanager.entity;

import com.filemanager.enums.Role;
import com.filemanager.enums.UserStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, length = 100)
    private String email;

    @Column(length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.PENDING;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 4: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/entity/User.java \
        file-manager-backend/src/main/java/com/filemanager/enums/
git commit -m "feat: add User entity and enums for authentication"
```

### Task 3: 创建UserRepository

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/repository/UserRepository.java`

- [ ] **Step 1: 创建UserRepository接口**

```java
package com.filemanager.repository;

import com.filemanager.entity.User;
import com.filemanager.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    Page<User> findByStatus(UserStatus status, Pageable pageable);
    
    Page<User> findByStatusIn(java.util.List<UserStatus> statuses, Pageable pageable);
}
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/repository/UserRepository.java
git commit -m "feat: add UserRepository for user data access"
```

### Task 4: 创建DTO类

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/dto/LoginRequest.java`
- Create: `file-manager-backend/src/main/java/com/filemanager/dto/RegisterRequest.java`
- Create: `file-manager-backend/src/main/java/com/filemanager/dto/UserDTO.java`

- [ ] **Step 1: 创建LoginRequest DTO**

```java
package com.filemanager.dto;

public class LoginRequest {
    private String username;
    private String password;
    private Boolean rememberMe = false;

    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Boolean getRememberMe() { return rememberMe; }
    public void setRememberMe(Boolean rememberMe) { this.rememberMe = rememberMe; }
}
```

- [ ] **Step 2: 创建RegisterRequest DTO**

```java
package com.filemanager.dto;

public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private String nickname;

    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
```

- [ ] **Step 3: 创建UserDTO**

```java
package com.filemanager.dto;

import com.filemanager.enums.Role;
import com.filemanager.enums.UserStatus;
import java.time.LocalDateTime;

public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String nickname;
    private Role role;
    private UserStatus status;
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/dto/LoginRequest.java \
        file-manager-backend/src/main/java/com/filemanager/dto/RegisterRequest.java \
        file-manager-backend/src/main/java/com/filemanager/dto/UserDTO.java
git commit -m "feat: add DTOs for authentication and user management"
```

### Task 5: 配置Spring Security

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/config/SecurityConfig.java`
- Create: `file-manager-backend/src/main/java/com/filemanager/security/CustomUserDetailsService.java`

- [ ] **Step 1: 创建CustomUserDetailsService**

```java
package com.filemanager.security;

import com.filemanager.entity.User;
import com.filemanager.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getStatus().name().equals("ACTIVE"),
                true,
                true,
                true,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
```

- [ ] **Step 2: 创建SecurityConfig**

```java
package com.filemanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173", "http://localhost:16666"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/config/SecurityConfig.java \
        file-manager-backend/src/main/java/com/filemanager/security/
git commit -m "feat: configure Spring Security with session-based authentication"
```

### Task 6: 配置Session超时

**Files:**
- Modify: `file-manager-backend/src/main/resources/application.yml`

- [ ] **Step 1: 在application.yml中添加Session配置**

在文件末尾添加：

```yaml
server:
  servlet:
    session:
      timeout: 7d
      cookie:
        http-only: true
        same-site: lax
        name: FILE_MANAGER_SESSION
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-backend/src/main/resources/application.yml
git commit -m "feat: configure session timeout and cookie settings"
```

## Phase 2: 后端认证功能

### Task 7: 创建UserService

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/service/UserService.java`

- [ ] **Step 1: 创建UserService类**

```java
package com.filemanager.service;

import com.filemanager.dto.UserDTO;
import com.filemanager.entity.User;
import com.filemanager.enums.Role;
import com.filemanager.enums.UserStatus;
import com.filemanager.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setNickname(user.getNickname());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }

    @Transactional
    public User createUser(String username, String password, String email, String nickname, Role role) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setNickname(nickname != null ? nickname : username);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    @Transactional
    public User registerUser(String username, String password, String email, String nickname) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setNickname(nickname != null ? nickname : username);
        user.setRole(Role.USER);
        user.setStatus(UserStatus.PENDING);

        return userRepository.save(user);
    }

    @Transactional
    public void approveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Transactional
    public void disableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
    }

    @Transactional
    public void enableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    public Page<User> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public Page<User> getUsersByStatus(UserStatus status, Pageable pageable) {
        return userRepository.findByStatus(status, pageable);
    }

    @Transactional
    public void updateUser(Long userId, String email, String nickname, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new RuntimeException("Email already exists");
            }
            user.setEmail(email);
        }
        
        if (nickname != null) {
            user.setNickname(nickname);
        }
        
        if (role != null) {
            user.setRole(role);
        }
        
        userRepository.save(user);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/service/UserService.java
git commit -m "feat: add UserService for user management operations"
```

### Task 8: 创建AuthController

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/controller/AuthController.java`

- [ ] **Step 1: 创建AuthController类**

```java
package com.filemanager.controller;

import com.filemanager.dto.ApiResponse;
import com.filemanager.dto.LoginRequest;
import com.filemanager.dto.RegisterRequest;
import com.filemanager.dto.UserDTO;
import com.filemanager.entity.User;
import com.filemanager.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<UserDTO> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);

        // 如果选择"记住我"，设置session超时为30天
        if (Boolean.TRUE.equals(request.getRememberMe())) {
            session.setMaxInactiveInterval(30 * 24 * 60 * 60);
        } else {
            session.setMaxInactiveInterval(7 * 24 * 60 * 60);
        }

        User user = userService.findByUsername(request.getUsername());
        return ApiResponse.success(userService.toDTO(user));
    }

    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody RegisterRequest request) {
        userService.registerUser(
                request.getUsername(),
                request.getPassword(),
                request.getEmail(),
                request.getNickname()
        );
        return ApiResponse.success("注册成功，请等待管理员审核");
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.success("登出成功");
    }

    @GetMapping("/me")
    public ApiResponse<UserDTO> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ApiResponse.error("未登录");
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username);
        if (user == null) {
            return ApiResponse.error("用户不存在");
        }

        return ApiResponse.success(userService.toDTO(user));
    }
}
```

- [ ] **Step 2: 在SecurityConfig中添加AuthenticationManager Bean**

修改 `SecurityConfig.java`，添加：

```java
@Bean
public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
    return http.getSharedObject(AuthenticationManager.class);
}
```

- [ ] **Step 3: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/controller/AuthController.java \
        file-manager-backend/src/main/java/com/filemanager/config/SecurityConfig.java
git commit -m "feat: add AuthController for login, register, logout"
```

### Task 9: 创建AdminUserController

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/controller/AdminUserController.java`

- [ ] **Step 1: 创建AdminUserController类**

```java
package com.filemanager.controller;

import com.filemanager.dto.ApiResponse;
import com.filemanager.dto.UserDTO;
import com.filemanager.entity.User;
import com.filemanager.enums.Role;
import com.filemanager.enums.UserStatus;
import com.filemanager.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Role role) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage;
        
        if (status != null) {
            userPage = userService.getUsersByStatus(status, pageable);
        } else {
            userPage = userService.getUsers(pageable);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("content", userPage.getContent().stream().map(userService::toDTO).toList());
        response.put("totalElements", userPage.getTotalElements());
        response.put("totalPages", userPage.getTotalPages());
        response.put("currentPage", userPage.getNumber());
        response.put("size", userPage.getSize());
        
        return ApiResponse.success(response);
    }

    @PostMapping
    public ApiResponse<UserDTO> createUser(@RequestBody Map<String, Object> request) {
        String username = (String) request.get("username");
        String password = (String) request.get("password");
        String email = (String) request.get("email");
        String nickname = (String) request.get("nickname");
        String roleStr = (String) request.get("role");
        Role role = roleStr != null ? Role.valueOf(roleStr) : Role.USER;

        User user = userService.createUser(username, password, email, nickname, role);
        return ApiResponse.success(userService.toDTO(user));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserDTO> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        
        String email = (String) request.get("email");
        String nickname = (String) request.get("nickname");
        String roleStr = (String) request.get("role");
        Role role = roleStr != null ? Role.valueOf(roleStr) : null;

        userService.updateUser(id, email, nickname, role);
        
        User user = userService.getUsers(PageRequest.of(0, 1))
                .getContent().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        return ApiResponse.success(userService.toDTO(user));
    }

    @PutMapping("/{id}/approve")
    public ApiResponse<String> approveUser(@PathVariable Long id) {
        userService.approveUser(id);
        return ApiResponse.success("审核通过");
    }

    @PutMapping("/{id}/disable")
    public ApiResponse<String> disableUser(@PathVariable Long id) {
        userService.disableUser(id);
        return ApiResponse.success("已禁用");
    }

    @PutMapping("/{id}/enable")
    public ApiResponse<String> enableUser(@PathVariable Long id) {
        userService.enableUser(id);
        return ApiResponse.success("已启用");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success("删除成功");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/controller/AdminUserController.java
git commit -m "feat: add AdminUserController for user management"
```

### Task 10: 创建默认管理员账号

**Files:**
- Create: `file-manager-backend/src/main/java/com/filemanager/config/DataInitializer.java`

- [ ] **Step 1: 创建DataInitializer类**

```java
package com.filemanager.config;

import com.filemanager.entity.User;
import com.filemanager.enums.Role;
import com.filemanager.enums.UserStatus;
import com.filemanager.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // 创建默认管理员账号
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@example.com");
            admin.setNickname("系统管理员");
            admin.setRole(Role.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            userRepository.save(admin);
            
            System.out.println("======================================");
            System.out.println("默认管理员账号已创建:");
            System.out.println("用户名: admin");
            System.out.println("密码: admin123");
            System.out.println("请登录后立即修改密码！");
            System.out.println("======================================");
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-backend/src/main/java/com/filemanager/config/DataInitializer.java
git commit -m "feat: add default admin account initialization"
```

## Phase 3: 前端登录注册

### Task 11: 安装前端依赖

**Files:**
- Modify: `file-manager-frontend/package.json`

- [ ] **Step 1: 验证依赖是否已存在**

Run: `cd file-manager-frontend && grep -E "pinia|vue-router" package.json`

如果已存在则跳过此任务。

- [ ] **Step 2: 如不存在则安装依赖**

Run: `cd file-manager-frontend && npm install pinia vue-router`

- [ ] **Step 3: Commit**

```bash
git add file-manager-frontend/package.json file-manager-frontend/package-lock.json
git commit -m "chore: ensure pinia and vue-router are installed"
```

### Task 12: 创建认证API

**Files:**
- Create: `file-manager-frontend/src/api/auth.js`

- [ ] **Step 1: 创建auth.js文件**

```javascript
import request from './request'

export const authApi = {
  login(data) {
    return request({
      url: '/api/auth/login',
      method: 'post',
      data
    })
  },

  register(data) {
    return request({
      url: '/api/auth/register',
      method: 'post',
      data
    })
  },

  logout() {
    return request({
      url: '/api/auth/logout',
      method: 'post'
    })
  },

  getCurrentUser() {
    return request({
      url: '/api/auth/me',
      method: 'get'
    })
  }
}
```

- [ ] **Step 2: 创建request工具（如果不存在）**

检查 `file-manager-frontend/src/api/request.js` 是否存在。如果不存在，创建：

```javascript
import axios from 'axios'

const request = axios.create({
  baseURL: '',
  timeout: 30000,
  withCredentials: true
})

request.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    return Promise.reject(error)
  }
)

export default request
```

- [ ] **Step 3: Commit**

```bash
git add file-manager-frontend/src/api/auth.js \
        file-manager-frontend/src/api/request.js
git commit -m "feat: add authentication API service"
```

### Task 13: 创建用户状态管理

**Files:**
- Create: `file-manager-frontend/src/store/user.js`

- [ ] **Step 1: 创建user store**

```javascript
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    user: null,
    initialized: false
  }),

  getters: {
    isLoggedIn: (state) => !!state.user,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    username: (state) => state.user?.username || '',
    nickname: (state) => state.user?.nickname || state.user?.username || '',
    userId: (state) => state.user?.id || null
  },

  actions: {
    async login(credentials) {
      const response = await authApi.login(credentials)
      if (response.code === 200) {
        this.user = response.data
      }
      return response
    },

    async register(userData) {
      const response = await authApi.register(userData)
      return response
    },

    async logout() {
      await authApi.logout()
      this.user = null
    },

    async fetchCurrentUser() {
      try {
        const response = await authApi.getCurrentUser()
        if (response.code === 200 && response.data) {
          this.user = response.data
        } else {
          this.user = null
        }
      } catch (error) {
        this.user = null
      } finally {
        this.initialized = true
      }
    },

    setUser(userData) {
      this.user = userData
    },

    clearUser() {
      this.user = null
    }
  }
})
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-frontend/src/store/user.js
git commit -m "feat: add user store for state management"
```

### Task 14: 创建登录页面

**Files:**
- Create: `file-manager-frontend/src/views/Login.vue`

- [ ] **Step 1: 创建Login.vue组件**

```vue
<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <h2>文件管理系统</h2>
        </div>
      </template>
      
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        
        <el-form-item>
          <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            size="large"
            style="width: 100%"
          >
            登录
          </el-button>
        </el-form-item>
        
        <div class="login-footer">
          <span>还没有账号？</span>
          <router-link to="/register">立即注册</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  rememberMe: false
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const response = await userStore.login(loginForm)
        if (response.code === 200) {
          ElMessage.success('登录成功')
          router.push('/')
        } else {
          ElMessage.error(response.message || '登录失败')
        }
      } catch (error) {
        ElMessage.error('登录失败，请重试')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0;
  color: #303133;
}

.login-footer {
  text-align: center;
  margin-top: 10px;
}

.login-footer a {
  color: #409eff;
  text-decoration: none;
  margin-left: 5px;
}

.login-footer a:hover {
  text-decoration: underline;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-frontend/src/views/Login.vue
git commit -m "feat: add Login page component"
```

### Task 15: 创建注册页面

**Files:**
- Create: `file-manager-frontend/src/views/Register.vue`

- [ ] **Step 1: 创建Register.vue组件**

```vue
<template>
  <div class="register-container">
    <el-card class="register-card">
      <template #header>
        <div class="card-header">
          <h2>注册账号</h2>
        </div>
      </template>
      
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        @submit.prevent="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="用户名（3-20字符）"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        
        <el-form-item prop="email">
          <el-input
            v-model="registerForm.email"
            placeholder="邮箱"
            prefix-icon="Message"
            size="large"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="密码（6-20字符）"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="确认密码"
            prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        
        <el-form-item prop="nickname">
          <el-input
            v-model="registerForm.nickname"
            placeholder="昵称（可选）"
            prefix-icon="UserFilled"
            size="large"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            size="large"
            style="width: 100%"
          >
            注册
          </el-button>
        </el-form-item>
        
        <div class="register-footer">
          <span>已有账号？</span>
          <router-link to="/login">立即登录</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const registerFormRef = ref(null)
const loading = ref(false)

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  nickname: ''
})

const validateUsername = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入用户名'))
  } else if (value.length < 3 || value.length > 20) {
    callback(new Error('用户名长度为3-20个字符'))
  } else if (!/^[a-zA-Z0-9_]+$/.test(value)) {
    callback(new Error('用户名只能包含字母、数字和下划线'))
  } else {
    callback()
  }
}

const validateEmail = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入邮箱'))
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    callback(new Error('请输入有效的邮箱地址'))
  } else {
    callback()
  }
}

const validatePassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入密码'))
  } else if (value.length < 6 || value.length > 20) {
    callback(new Error('密码长度为6-20个字符'))
  } else {
    callback()
  }
}

const validateConfirmPassword = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请确认密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, validator: validateUsername, trigger: 'blur' }
  ],
  email: [
    { required: true, validator: validateEmail, trigger: 'blur' }
  ],
  password: [
    { required: true, validator: validatePassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  if (!registerFormRef.value) return
  
  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const response = await userStore.register({
          username: registerForm.username,
          email: registerForm.email,
          password: registerForm.password,
          nickname: registerForm.nickname
        })
        
        if (response.code === 200) {
          ElMessage.success('注册成功，请等待管理员审核')
          router.push('/login')
        } else {
          ElMessage.error(response.message || '注册失败')
        }
      } catch (error) {
        ElMessage.error('注册失败，请重试')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.register-card {
  width: 450px;
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0;
  color: #303133;
}

.register-footer {
  text-align: center;
  margin-top: 10px;
}

.register-footer a {
  color: #409eff;
  text-decoration: none;
  margin-left: 5px;
}

.register-footer a:hover {
  text-decoration: underline;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-frontend/src/views/Register.vue
git commit -m "feat: add Register page component"
```

### Task 16: 配置路由和路由守卫

**Files:**
- Modify: `file-manager-frontend/src/router/index.js`
- Modify: `file-manager-frontend/src/main.js`

- [ ] **Step 1: 更新router/index.js**

```javascript
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'
import Login from '@/views/Login.vue'
import Register from '@/views/Register.vue'
import Home from '@/views/Home.vue'
import Admin from '@/views/Admin.vue'
import ServerManage from '@/views/admin/ServerManage.vue'
import UserManage from '@/views/admin/UserManage.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { guest: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: { guest: true }
  },
  {
    path: '/',
    name: 'Home',
    component: Home,
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: Admin,
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: 'servers',
        name: 'ServerManage',
        component: ServerManage
      },
      {
        path: 'users',
        name: 'UserManage',
        component: UserManage
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  
  // 等待用户信息初始化
  if (!userStore.initialized) {
    await userStore.fetchCurrentUser()
  }
  
  const isLoggedIn = userStore.isLoggedIn
  const isAdmin = userStore.isAdmin
  
  // 需要登录但未登录
  if (to.meta.requiresAuth && !isLoggedIn) {
    next('/login')
  }
  // 需要管理员权限但不是管理员
  else if (to.meta.requiresAdmin && !isAdmin) {
    next('/')
  }
  // 已登录访问登录/注册页
  else if (to.meta.guest && isLoggedIn) {
    next('/')
  }
  else {
    next()
  }
})

export default router
```

- [ ] **Step 2: 更新main.js引入Pinia**

```javascript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import App from './App.vue'
import router from './router'
import './assets/styles/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, {
  locale: zhCn
})

app.mount('#app')
```

- [ ] **Step 3: Commit**

```bash
git add file-manager-frontend/src/router/index.js \
        file-manager-frontend/src/main.js
git commit -m "feat: configure routes and navigation guards"
```

## Phase 4: 前端用户管理

### Task 17: 创建用户管理页面

**Files:**
- Create: `file-manager-frontend/src/views/admin/UserManage.vue`

- [ ] **Step 1: 创建UserManage.vue组件**

```vue
<template>
  <div class="user-manage">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" @click="showCreateDialog">
            创建用户
          </el-button>
        </div>
      </template>
      
      <!-- 筛选器 -->
      <div class="filters">
        <el-select v-model="filters.status" placeholder="用户状态" clearable @change="loadUsers">
          <el-option label="全部" value="" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已激活" value="ACTIVE" />
          <el-option label="已禁用" value="DISABLED" />
        </el-select>
      </div>
      
      <!-- 用户列表 -->
      <el-table :data="users" v-loading="loading" style="width: 100%">
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="email" label="邮箱" width="200" />
        <el-table-column prop="nickname" label="昵称" width="150" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'">
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="250">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              type="success"
              size="small"
              @click="handleApprove(row.id)"
            >
              审核
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              type="warning"
              size="small"
              @click="handleDisable(row.id)"
            >
              禁用
            </el-button>
            <el-button
              v-if="row.status === 'DISABLED'"
              type="success"
              size="small"
              @click="handleEnable(row.id)"
            >
              启用
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleDelete(row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>
    
    <!-- 创建用户对话框 -->
    <el-dialog v-model="createDialogVisible" title="创建用户" width="500px">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="createForm.email" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="createForm.nickname" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="createForm.role">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const loading = ref(false)
const users = ref([])
const filters = reactive({
  status: ''
})
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref(null)
const createForm = reactive({
  username: '',
  password: '',
  email: '',
  nickname: '',
  role: 'USER'
})

const createRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在3到20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在6到20个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

onMounted(() => {
  loadUsers()
})

const loadUsers = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size
    }
    if (filters.status) {
      params.status = filters.status
    }
    
    const response = await request({
      url: '/api/admin/users',
      method: 'get',
      params
    })
    
    if (response.code === 200) {
      users.value = response.data.content
      pagination.total = response.data.totalElements
    }
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  Object.assign(createForm, {
    username: '',
    password: '',
    email: '',
    nickname: '',
    role: 'USER'
  })
  createDialogVisible.value = true
}

const handleCreate = async () => {
  if (!createFormRef.value) return
  
  await createFormRef.value.validate(async (valid) => {
    if (valid) {
      createLoading.value = true
      try {
        const response = await request({
          url: '/api/admin/users',
          method: 'post',
          data: createForm
        })
        
        if (response.code === 200) {
          ElMessage.success('创建成功')
          createDialogVisible.value = false
          loadUsers()
        } else {
          ElMessage.error(response.message || '创建失败')
        }
      } catch (error) {
        ElMessage.error('创建失败')
      } finally {
        createLoading.value = false
      }
    }
  })
}

const handleApprove = async (id) => {
  try {
    await ElMessageBox.confirm('确定审核通过该用户？', '提示', {
      type: 'success'
    })
    
    const response = await request({
      url: `/api/admin/users/${id}/approve`,
      method: 'put'
    })
    
    if (response.code === 200) {
      ElMessage.success('审核通过')
      loadUsers()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleDisable = async (id) => {
  try {
    await ElMessageBox.confirm('确定禁用该用户？', '提示', {
      type: 'warning'
    })
    
    const response = await request({
      url: `/api/admin/users/${id}/disable`,
      method: 'put'
    })
    
    if (response.code === 200) {
      ElMessage.success('已禁用')
      loadUsers()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleEnable = async (id) => {
  try {
    await ElMessageBox.confirm('确定启用该用户？', '提示', {
      type: 'success'
    })
    
    const response = await request({
      url: `/api/admin/users/${id}/enable`,
      method: 'put'
    })
    
    if (response.code === 200) {
      ElMessage.success('已启用')
      loadUsers()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除该用户？此操作不可恢复！', '警告', {
      type: 'error'
    })
    
    const response = await request({
      url: `/api/admin/users/${id}`,
      method: 'delete'
    })
    
    if (response.code === 200) {
      ElMessage.success('删除成功')
      loadUsers()
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const getStatusType = (status) => {
  const types = {
    ACTIVE: 'success',
    PENDING: 'warning',
    DISABLED: 'info'
  }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = {
    ACTIVE: '已激活',
    PENDING: '待审核',
    DISABLED: '已禁用'
  }
  return texts[status] || status
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.user-manage {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filters {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-frontend/src/views/admin/UserManage.vue
git commit -m "feat: add UserManage component for admin"
```

### Task 18: 更新Admin页面布局

**Files:**
- Modify: `file-manager-frontend/src/views/Admin.vue`

- [ ] **Step 1: 更新Admin.vue为布局组件**

```vue
<template>
  <div class="admin-layout">
    <el-container>
      <el-aside width="200px">
        <div class="logo">
          <h3>管理后台</h3>
        </div>
        <el-menu
          :default-active="activeMenu"
          router
        >
          <el-menu-item index="/admin/servers">
            <el-icon><Monitor /></el-icon>
            <span>服务器管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      
      <el-container>
        <el-header>
          <div class="header-content">
            <span>欢迎，{{ userStore.nickname }}</span>
            <el-button type="text" @click="goHome">返回首页</el-button>
          </div>
        </el-header>
        
        <el-main>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { Monitor, User } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

const goHome = () => {
  router.push('/')
}
</script>

<style scoped>
.admin-layout {
  height: 100vh;
}

.el-container {
  height: 100%;
}

.el-aside {
  background-color: #545c64;
  color: #fff;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #434a50;
}

.logo h3 {
  margin: 0;
  color: #fff;
}

.el-menu {
  border-right: none;
}

.el-header {
  background-color: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.el-main {
  background-color: #f0f2f5;
  padding: 0;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-frontend/src/views/Admin.vue
git commit -m "feat: update Admin layout with sidebar navigation"
```

### Task 19: 更新AppHeader显示用户信息

**Files:**
- Modify: `file-manager-frontend/src/components/layout/AppHeader.vue`

- [ ] **Step 1: 更新AppHeader添加用户菜单**

在现有代码基础上添加用户下拉菜单：

```vue
<!-- 在适当位置添加 -->
<div class="user-info" v-if="userStore.isLoggedIn">
  <el-dropdown @command="handleCommand">
    <span class="user-dropdown">
      <el-icon><UserFilled /></el-icon>
      <span>{{ userStore.nickname }}</span>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-if="userStore.isAdmin" command="admin">
          管理后台
        </el-dropdown-item>
        <el-dropdown-item command="logout">
          退出登录
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</div>
<div v-else>
  <el-button type="primary" @click="$router.push('/login')">
    登录
  </el-button>
</div>
```

并在script中添加：

```javascript
import { UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const router = useRouter()

const handleCommand = async (command) => {
  if (command === 'logout') {
    await userStore.logout()
    router.push('/login')
  } else if (command === 'admin') {
    router.push('/admin/servers')
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add file-manager-frontend/src/components/layout/AppHeader.vue
git commit -m "feat: add user dropdown menu to AppHeader"
```

## Phase 5: 测试和文档

### Task 20: 提交所有更改

- [ ] **Step 1: 检查所有文件已提交**

Run: `git status`

确保所有源代码文件都已添加和提交。

- [ ] **Step 2: 推送到远程仓库**

```bash
git push origin main
```

---

## 实施说明

**执行顺序：** 严格按照任务编号顺序执行（Task 1 → Task 20）

**每个任务的步骤：** 每个任务内的步骤也必须按顺序执行

**验证要求：**
- 每个Step执行后都要验证结果
- 如果遇到错误，必须修复后才能继续下一步
- 所有测试必须通过后才能commit

**注意事项：**
1. 确保数据库服务正在运行
2. 后端默认在1001端口，前端在5173端口
3. 默认管理员账号: admin / admin123
4. 首次部署后立即修改默认管理员密码

**完成标志：**
- 所有20个任务完成
- 所有代码已提交
- 可以正常登录/注册/登出
- 管理员可以访问管理后台
- 用户权限控制正常工作