package com.filemanager.controller;

import com.filemanager.dto.AccessHistoryDTO;
import com.filemanager.dto.AccessHistoryRequest;
import com.filemanager.dto.ApiResponse;
import com.filemanager.service.AccessHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
public class AccessHistoryController {

    @Autowired
    private AccessHistoryService historyService;

    @GetMapping
    public ApiResponse<List<AccessHistoryDTO>> list() {
        return ApiResponse.success(historyService.listRecent());
    }

    @PostMapping
    public ApiResponse<AccessHistoryDTO> record(@RequestBody AccessHistoryRequest request) {
        return ApiResponse.success(historyService.record(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        historyService.remove(id);
        return ApiResponse.success("已删除", null);
    }

    @DeleteMapping
    public ApiResponse<Void> clear() {
        historyService.clear();
        return ApiResponse.success("已清空", null);
    }
}
