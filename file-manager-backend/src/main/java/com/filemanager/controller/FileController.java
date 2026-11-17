package com.filemanager.controller;

import com.filemanager.dto.ApiResponse;
import com.filemanager.dto.FileDTO;
import com.filemanager.entity.ServerConfig;
import com.filemanager.repository.ServerConfigRepository;
import com.filemanager.service.FileService;
import com.filemanager.util.PathValidator;
import com.filemanager.util.RangedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    @Autowired
    private ServerConfigRepository serverConfigRepository;

    @Autowired
    private PathValidator pathValidator;
    
    @GetMapping("/tree")
    public ApiResponse<List<FileDTO>> getFileTree(
            @RequestParam Long serverId,
            @RequestParam(defaultValue = "/") String path) {
        return ApiResponse.success(fileService.getFileTree(serverId, path));
    }

    /**
     * 递归取目录树, 供关系图使用(懒加载时 maxDepth 传 1)。
     * includeFiles=true 时文件也作为叶子节点返回, 每个目录最多 maxFilesPerDir 个;
     * maxDepth / maxNodes 用于防止超大目录拖死。
     */
    @GetMapping("/directory-tree")
    public ApiResponse<List<FileDTO>> getDirectoryTree(
            @RequestParam Long serverId,
            @RequestParam(defaultValue = "/") String path,
            @RequestParam(defaultValue = "5") Integer maxDepth,
            @RequestParam(defaultValue = "180") Integer maxNodes,
            @RequestParam(defaultValue = "true") Boolean includeFiles,
            @RequestParam(defaultValue = "60") Integer maxFilesPerDir) {
        return ApiResponse.success(fileService.getDirectoryTree(
                serverId, path, maxDepth, maxNodes, includeFiles, maxFilesPerDir));
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
            @RequestParam String path,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
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
        } else if (extension.endsWith(".flv")) {
            mimeType = "video/x-flv";
            disposition = "inline";
        } else if (extension.endsWith(".wmv")) {
            mimeType = "video/x-ms-wmv";
            disposition = "inline";
        } else if (extension.endsWith(".pdf")) {
            mimeType = "application/pdf";
            disposition = "inline";
        } else if (extension.endsWith(".jpg") || extension.endsWith(".jpeg")) {
            mimeType = "image/jpeg";
            disposition = "inline";
        } else if (extension.endsWith(".png")) {
            mimeType = "image/png";
            disposition = "inline";
        } else if (extension.endsWith(".gif")) {
            mimeType = "image/gif";
            disposition = "inline";
        } else if (extension.endsWith(".webp")) {
            mimeType = "image/webp";
            disposition = "inline";
        } else if (extension.endsWith(".svg")) {
            mimeType = "image/svg+xml";
            disposition = "inline";
        }

        Long totalSize = fileService.getFileSize(serverId, path);
        Long[] range = RangedResponse.parseRange(rangeHeader, totalSize);

        return RangedResponse.build(
                rangeHeader,
                totalSize,
                MediaType.valueOf(mimeType),
                disposition,
                encodedFilename,
                () -> range != null
                        ? fileService.downloadFile(serverId, path, range[0], range[1])
                        : fileService.downloadFile(serverId, path));
    }

    
    @PutMapping("/{serverId}/rename")
    public ApiResponse<Void> renameFile(
            @PathVariable Long serverId,
            @RequestParam String oldPath,
            @RequestParam String newPath) {
        try {
            pathValidator.validatePath(oldPath);
            pathValidator.validatePath(newPath);
            fileService.renameFile(serverId, oldPath, newPath);
            return ApiResponse.success("重命名成功", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("无效的路径: " + e.getMessage());
        }
    }

    @DeleteMapping("/{serverId}")
    public ApiResponse<Void> deleteFile(
            @PathVariable Long serverId,
            @RequestParam String path) {
        try {
            pathValidator.validatePath(path);
            fileService.deleteFile(serverId, path);
            return ApiResponse.success("删除成功", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("无效的路径: " + e.getMessage());
        }
    }

    @PostMapping("/create-folder")
    public ApiResponse<Void> createFolder(
            @RequestParam Long serverId,
            @RequestParam String path) {
        try {
            pathValidator.validatePath(path);
            fileService.createFolder(serverId, path);
            return ApiResponse.success("创建成功", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("无效的路径: " + e.getMessage());
        }
    }
    
    @PostMapping("/sync")
    public ApiResponse<Map<String, Integer>> syncFiles(
            @RequestParam Long serverId,
            @RequestParam(defaultValue = "/") String path) {
        Map<String, Integer> result = fileService.syncFiles(serverId, path);
        return ApiResponse.success("同步成功", result);
    }
    
    @GetMapping("/export")
    public ResponseEntity<Resource> exportDirectory(
            @RequestParam Long serverId,
            @RequestParam(defaultValue = "/") String path) {
        List<FileDTO> files = fileService.getAllFilesRecursive(serverId, path);
        
        ServerConfig server = serverConfigRepository.findById(serverId).orElse(null);
        String baseUrl = server != null ? server.getUrl() : "";
        
        for (FileDTO file : files) {
            if (file.getPath() != null && baseUrl != null && !baseUrl.isEmpty()) {
                String filePath = file.getPath();
                if (filePath.startsWith("/")) {
                    file.setFullPath(baseUrl + filePath);
                } else {
                    file.setFullPath(baseUrl + "/" + filePath);
                }
            } else {
                file.setFullPath(file.getPath());
            }
        }
        
        String jsonContent;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            jsonContent = mapper.writeValueAsString(files);
        } catch (Exception e) {
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
        
        String filename = "directory_" + path.replace("/", "_").replaceAll("^_+$", "root") + ".json";
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        
        InputStream stream = new java.io.ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8));
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"")
                .body(new InputStreamResource(stream));
    }
}