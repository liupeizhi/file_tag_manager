package com.filemanager.controller;

import com.filemanager.dto.ApiResponse;
import com.filemanager.dto.DuplicateCompareRequest;
import com.filemanager.dto.DuplicateGroupDTO;
import com.filemanager.dto.DuplicateGroupPageDTO;
import com.filemanager.dto.DuplicateTaskDTO;
import com.filemanager.service.DuplicateScanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 目录重复文件检测（只读，不删除任何文件）。
 */
@RestController
@RequestMapping("/api/duplicates")
public class DuplicateController {

    @Autowired
    private DuplicateScanService duplicateScanService;

    @PostMapping("/compare")
    public ApiResponse<Map<String, String>> compare(@RequestBody DuplicateCompareRequest request) {
        try {
            String taskId = duplicateScanService.createTask(request);
            return ApiResponse.success(Map.of("taskId", taskId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<DuplicateTaskDTO> getTask(@PathVariable String taskId) {
        try {
            return ApiResponse.success(duplicateScanService.getTask(taskId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @GetMapping("/tasks/{taskId}/groups")
    public ApiResponse<DuplicateGroupPageDTO> getGroups(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean crossOnly,
            @RequestParam(required = false) Long minSize,
            @RequestParam(required = false) String verified) {
        try {
            return ApiResponse.success(
                    duplicateScanService.listGroups(taskId, page, size, crossOnly, minSize, verified));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping("/tasks/{taskId}/groups/{groupId}/verify")
    public ApiResponse<DuplicateGroupDTO> verify(
            @PathVariable String taskId,
            @PathVariable String groupId) {
        try {
            return ApiResponse.success(duplicateScanService.verifyGroup(taskId, groupId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String taskId) {
        try {
            duplicateScanService.cancel(taskId);
            return ApiResponse.success("已取消", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @GetMapping("/tasks/{taskId}/export")
    public ResponseEntity<byte[]> export(@PathVariable String taskId) {
        byte[] csv;
        try {
            csv = duplicateScanService.exportCsv(taskId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        String filename = URLEncoder.encode("duplicates-" + taskId + ".csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
