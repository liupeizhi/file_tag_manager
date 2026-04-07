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
            @RequestParam(required = false) UserStatus status) {
        
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