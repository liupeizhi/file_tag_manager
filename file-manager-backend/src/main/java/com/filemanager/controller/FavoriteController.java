package com.filemanager.controller;

import com.filemanager.dto.ApiResponse;
import com.filemanager.dto.FavoriteDTO;
import com.filemanager.dto.FavoriteRequest;
import com.filemanager.dto.FavoriteToggleResult;
import com.filemanager.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @GetMapping
    public ApiResponse<List<FavoriteDTO>> list() {
        return ApiResponse.success(favoriteService.list());
    }

    @PostMapping("/toggle")
    public ApiResponse<FavoriteToggleResult> toggle(@RequestBody FavoriteRequest request) {
        return ApiResponse.success(favoriteService.toggle(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        favoriteService.remove(id);
        return ApiResponse.success("已取消收藏", null);
    }

    @DeleteMapping
    public ApiResponse<Void> clear() {
        favoriteService.clear();
        return ApiResponse.success("已清空收藏", null);
    }
}
