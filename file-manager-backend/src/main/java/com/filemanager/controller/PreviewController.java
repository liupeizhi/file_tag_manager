package com.filemanager.controller;

import com.filemanager.service.FileService;
import com.filemanager.service.EbookConverterService;
import com.filemanager.service.EbookConverterService.CalibreNotInstalledException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.InputStream;
import java.io.File;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/preview")
public class PreviewController {

    @Autowired
    private FileService fileService;

    @Autowired
    private EbookConverterService ebookConverterService;

    @GetMapping("/{serverId}/text")
    public ResponseEntity<Resource> previewText(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .contentType(MediaType.TEXT_PLAIN)
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{serverId}/image")
    public ResponseEntity<Resource> previewImage(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);
        String contentType = getImageContentType(path);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .contentType(MediaType.valueOf(contentType))
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{serverId}/video")
    public ResponseEntity<Resource> previewVideo(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .contentType(MediaType.valueOf("video/mp4"))
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{serverId}/audio")
    public ResponseEntity<Resource> previewAudio(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);

        String mimeType = "audio/mpeg";
        String extension = path.toLowerCase();
        if (extension.endsWith(".wav")) {
            mimeType = "audio/wav";
        } else if (extension.endsWith(".flac")) {
            mimeType = "audio/flac";
        } else if (extension.endsWith(".aac")) {
            mimeType = "audio/aac";
        } else if (extension.endsWith(".m4a")) {
            mimeType = "audio/mp4";
        } else if (extension.endsWith(".ogg")) {
            mimeType = "audio/ogg";
        } else if (extension.endsWith(".wma")) {
            mimeType = "audio/x-ms-wma";
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .contentType(MediaType.valueOf(mimeType))
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{serverId}/pdf")
    public ResponseEntity<Resource> previewPdf(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{serverId}/document")
    public ResponseEntity<Resource> previewDocument(
            @PathVariable Long serverId,
            @RequestParam String path) {
        InputStream stream = fileService.downloadFile(serverId, path);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{serverId}/ebook")
    public ResponseEntity<?> previewEbook(
            @PathVariable Long serverId,
            @RequestParam String path,
            @RequestParam String filename) {
        try {
            InputStream stream = fileService.downloadFile(serverId, path);
            String ext = filename.toLowerCase();

            if (ext.endsWith(".epub")) {
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                        .contentType(MediaType.valueOf("application/epub+zip"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .body(new InputStreamResource(stream));
            }

            File epubFile = ebookConverterService.convertToEpub(stream, filename);

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                    .contentType(MediaType.valueOf("application/epub+zip"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"converted.epub\"")
                    .body(new FileSystemResource(epubFile));
        } catch (CalibreNotInstalledException e) {
            return ResponseEntity.status(422)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"" + e.getMessage() + "\", \"calibreRequired\": true}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    private String getImageContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "image/jpeg";
    }
}