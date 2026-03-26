package com.filemanager.controller;

import com.filemanager.dto.ApiResponse;
import com.filemanager.dto.ServerConfigDTO;
import com.filemanager.service.ServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/servers")
public class ServerController {
    
    @Autowired
    private ServerService serverService;
    
    @GetMapping
    public ApiResponse<List<ServerConfigDTO>> getAllServers() {
        return ApiResponse.success(serverService.getAllServers());
    }
    
    @GetMapping("/{id}")
    public ApiResponse<ServerConfigDTO> getServerById(@PathVariable Long id) {
        return ApiResponse.success(serverService.getServerById(id));
    }
    
    @PostMapping
    public ApiResponse<ServerConfigDTO> addServer(@RequestBody ServerConfigDTO dto) {
        return ApiResponse.success("添加成功", serverService.addServer(dto));
    }
    
    @PutMapping("/{id}")
    public ApiResponse<ServerConfigDTO> updateServer(@PathVariable Long id, 
                                                       @RequestBody ServerConfigDTO dto) {
        return ApiResponse.success("更新成功", serverService.updateServer(id, dto));
    }
    
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteServer(@PathVariable Long id) {
        serverService.deleteServer(id);
        return ApiResponse.success("删除成功", null);
    }
    
    @PostMapping("/{id}/test")
    public ApiResponse<Boolean> testConnection(@PathVariable Long id) {
        boolean result = serverService.testConnection(id);
        return ApiResponse.success(result ? "连接成功" : "连接失败", result);
    }
}