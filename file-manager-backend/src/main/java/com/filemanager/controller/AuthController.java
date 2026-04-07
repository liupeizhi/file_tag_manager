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