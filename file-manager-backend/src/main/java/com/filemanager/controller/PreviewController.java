package com.filemanager.controller;

import com.filemanager.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.InputStream;

@RestController
@RequestMapping("/api/preview")
public class PreviewController {
    
    @Autowired
    private FileService fileService;
    
    @GetMapping("/{serverId}/text")
    public ResponseEntity<Resource> previewText(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(new InputStreamResource(stream));
    }
    
    @GetMapping("/{serverId}/image")
    public ResponseEntity<Resource> previewImage(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(new InputStreamResource(stream));
    }
    
    @GetMapping("/{serverId}/video")
    public ResponseEntity<Resource> previewVideo(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("video/mp4"))
                .body(new InputStreamResource(stream));
    }
    
    @GetMapping("/{serverId}/audio")
    public ResponseEntity<Resource> previewAudio(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(stream));
    }
}