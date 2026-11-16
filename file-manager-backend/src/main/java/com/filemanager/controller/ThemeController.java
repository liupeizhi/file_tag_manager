package com.filemanager.controller;

import com.filemanager.dto.ApiResponse;
import com.filemanager.dto.ThemeDTO;
import com.filemanager.service.ThemeService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/theme")
public class ThemeController {

    @Autowired
    private ThemeService themeService;

    @GetMapping
    public ApiResponse<ThemeDTO> getTheme() {
        return ApiResponse.success(themeService.getTheme());
    }

    @PutMapping
    public ApiResponse<ThemeDTO> updateTheme(@RequestBody JsonNode request) {
        return ApiResponse.success(themeService.updateTheme(request));
    }

    @PostMapping("/background")
    public ApiResponse<ThemeDTO> uploadBackground(@RequestParam("background") MultipartFile file) {
        return ApiResponse.success(themeService.uploadBackground(file));
    }

    @GetMapping("/background")
    public ResponseEntity<byte[]> getBackground() {
        ThemeService.BackgroundFile file = themeService.readBackground();
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(file.getMime()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .eTag("\"theme-background-" + file.getUpdatedAt() + "\"")
                .body(file.getData());
    }

    @DeleteMapping("/background")
    public ApiResponse<ThemeDTO> removeBackground() {
        return ApiResponse.success(themeService.removeBackground());
    }
}
