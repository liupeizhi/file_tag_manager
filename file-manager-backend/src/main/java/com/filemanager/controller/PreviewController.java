package com.filemanager.controller;

import com.filemanager.entity.FileMetadata;
import com.filemanager.exception.VideoConversionException;
import com.filemanager.service.FileService;
import com.filemanager.service.EbookConverterService;
import com.filemanager.service.EbookConverterService.CalibreNotInstalledException;
import com.filemanager.service.VideoConverterService;
import com.filemanager.util.RangedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/preview")
public class PreviewController {

    @Autowired
    private FileService fileService;

    @Autowired
    private EbookConverterService ebookConverterService;

    @Autowired
    private VideoConverterService videoConverterService;

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

    /**
     * 视频预览。
     *
     * 浏览器原生只支持 MP4/WebM/M4V 等少数容器; AVI、RMVB、WMV、FLV、MOV 等
     * 连解复用器都没有, 必须由服务端转码成 MP4(H.264) 后才能播放。
     * 转码结果按内容指纹缓存, 同一文件只转一次。
     */
    @GetMapping("/{serverId}/video")
    public ResponseEntity<Resource> previewVideo(
            @PathVariable Long serverId,
            @RequestParam String path,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        String filename = path.substring(path.lastIndexOf('/') + 1);
        String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);

        if (videoConverterService.needsConversion(path)) {
            return serveConvertedVideo(serverId, path, rangeHeader, encodedFilename);
        }

        // 浏览器原生支持的格式: 透传原始流, 并支持 Range
        Long totalSize = fileService.getFileSize(serverId, path);
        Long[] range = RangedResponse.parseRange(rangeHeader, totalSize);

        return RangedResponse.build(
                rangeHeader,
                totalSize,
                MediaType.valueOf(videoContentType(path)),
                "inline",
                encodedFilename,
                () -> range != null
                        ? fileService.downloadFile(serverId, path, range[0], range[1])
                        : fileService.downloadFile(serverId, path));
    }

    /**
     * 转码后以本地文件方式提供, 完整支持 Range(可拖动进度条)。
     */
    private ResponseEntity<Resource> serveConvertedVideo(
            Long serverId, String path, String rangeHeader, String encodedFilename) {

        FileMetadata metadata = fileService.getMetadata(serverId, path);
        Path mp4 = videoConverterService.getOrConvert(serverId, path, metadata);

        long size;
        try {
            size = Files.size(mp4);
        } catch (IOException e) {
            throw new VideoConversionException("无法读取转码结果: " + e.getMessage());
        }

        return RangedResponse.build(
                rangeHeader,
                size,
                MediaType.valueOf("video/mp4"),
                "inline",
                encodedFilename,
                () -> {
                    try {
                        return Files.newInputStream(mp4);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    private String videoContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".m4v")) return "video/mp4";
        if (lower.endsWith(".ogv")) return "video/ogg";
        return "video/mp4";
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