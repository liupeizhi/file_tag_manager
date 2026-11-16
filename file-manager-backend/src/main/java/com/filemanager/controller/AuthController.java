package com.filemanager.controller;

import com.filemanager.dto.ApiResponse;
import com.filemanager.dto.LoginRequest;
import com.filemanager.dto.RegisterRequest;
import com.filemanager.dto.UserDTO;
import com.filemanager.entity.User;
import com.filemanager.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${server.servlet.session.cookie.name:FILE_MANAGER_SESSION}")
    private String sessionCookieName;

    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    public AuthController(AuthenticationManager authenticationManager, UserService userService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ApiResponse<UserDTO> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);

        if (Boolean.TRUE.equals(request.getRememberMe())) {
            session.setMaxInactiveInterval(30 * 24 * 60 * 60);
            // 只延长服务端会话不够: 容器默认下发的会话 cookie 没有 Max-Age,
            // 浏览器一关就没了。这里补一个同名、带 Max-Age 的 cookie,
            // 让登录态能跨浏览器重启保留。
            Cookie cookie = new Cookie(sessionCookieName, session.getId());
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(30 * 24 * 60 * 60);
            cookie.setAttribute("SameSite", "Lax");
            httpResponse.addCookie(cookie);
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
    public ResponseEntity<ApiResponse<UserDTO>> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 匿名认证令牌在 Spring Security 里也算 "authenticated",
        // 不单独判断的话, 退出登录后这里会拿着 "anonymousUser" 去查库,
        // 结果是 500 "用户不存在" 而不是应有的 401。
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未登录"));
        }

        String username = authentication.getName();
        if (username == null || username.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未登录"));
        }

        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "用户不存在"));
        }

        return ResponseEntity.ok(ApiResponse.success(userService.toDTO(user)));
    }
}