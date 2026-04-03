package com.filemanager.controller;

import com.filemanager.dto.ApiResponse;
import com.filemanager.dto.FileDTO;
import com.filemanager.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    
    @Autowired
    private FileService fileService;
    
    @GetMapping("/tree")
    public ApiResponse<List<FileDTO>> getFileTree(
            @RequestParam Long serverId,
            @RequestParam(defaultValue = "/") String path) {
        return ApiResponse.success(fileService.getFileTree(serverId, path));
    }
    
    @GetMapping
    public ApiResponse<Page<FileDTO>> getFileList(
            @RequestParam(required = false) Long serverId,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(fileService.getFileList(
                serverId, path, name, type, startDate, endDate, sortBy, sortOrder, page, size));
    }
    
    @GetMapping("/{serverId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);
        
        String filename = path.substring(path.lastIndexOf('/') + 1);
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        String extension = path.toLowerCase();
        
        String mimeType = "application/octet-stream";
        String disposition = "attachment";
        
        if (extension.endsWith(".wav")) {
            mimeType = "audio/wav";
            disposition = "inline";
        } else if (extension.endsWith(".mp3")) {
            mimeType = "audio/mpeg";
            disposition = "inline";
        } else if (extension.endsWith(".flac")) {
            mimeType = "audio/flac";
            disposition = "inline";
        } else if (extension.endsWith(".aac")) {
            mimeType = "audio/aac";
            disposition = "inline";
        } else if (extension.endsWith(".m4a")) {
            mimeType = "audio/mp4";
            disposition = "inline";
        } else if (extension.endsWith(".ogg")) {
            mimeType = "audio/ogg";
            disposition = "inline";
        } else if (extension.endsWith(".wma")) {
            mimeType = "audio/x-ms-wma";
            disposition = "inline";
        } else if (extension.endsWith(".mp4") || extension.endsWith(".m4v")) {
            mimeType = "video/mp4";
            disposition = "inline";
        } else if (extension.endsWith(".webm")) {
            mimeType = "video/webm";
            disposition = "inline";
        } else if (extension.endsWith(".mkv")) {
            mimeType = "video/x-matroska";
            disposition = "inline";
        } else if (extension.endsWith(".mov")) {
            mimeType = "video/quicktime";
            disposition = "inline";
        } else if (extension.endsWith(".avi")) {
            mimeType = "video/x-msvideo";
            disposition = "inline";
        } else if (extension.endsWith(".pdf")) {
            mimeType = "application/pdf";
            disposition = "inline";
        }
        
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                        disposition + "; filename=\"" + encodedFilename + "\"")
                .body(new InputStreamResource(stream));
    }
    
    @PostMapping("/upload")
    public ApiResponse<Void> uploadFile(
            @RequestParam Long serverId,
            @RequestParam String path,
            @RequestParam MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
            fileService.uploadFile(serverId, path, file.getOriginalFilename(), bais);
            return ApiResponse.success("上传成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{serverId}/rename")
    public ApiResponse<Void> renameFile(
            @PathVariable Long serverId,
            @RequestParam String oldPath,
            @RequestParam String newPath) {
        fileService.renameFile(serverId, oldPath, newPath);
        return ApiResponse.success("重命名成功", null);
    }
    
    @DeleteMapping("/{serverId}")
    public ApiResponse<Void> deleteFile(
            @PathVariable Long serverId,
            @RequestParam String path) {
        fileService.deleteFile(serverId, path);
        return ApiResponse.success("删除成功", null);
    }
    
    @PostMapping("/create-folder")
    public ApiResponse<Void> createFolder(
            @RequestParam Long serverId,
            @RequestParam String path) {
        fileService.createFolder(serverId, path);
        return ApiResponse.success("创建成功", null);
    }
    
    @PostMapping("/sync")
    public ApiResponse<Map<String, Integer>> syncFiles(
            @RequestParam Long serverId,
            @RequestParam(defaultValue = "/") String path) {
        Map<String, Integer> result = fileService.syncFiles(serverId, path);
        return ApiResponse.success("同步成功", result);
    }
}