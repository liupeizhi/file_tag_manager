package com.filemanager.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PathValidator {

    @Value("${app.file.path.max-length:4096}")
    private int maxPathLength;

    @Value("${app.file.path.max-depth:50}")
    private int maxDepth;

    public void validatePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be empty");
        }

        if (path.length() > maxPathLength) {
            throw new IllegalArgumentException("Path too long (max " + maxPathLength + " characters)");
        }

        String normalized = normalizePath(path);

        if (normalized.contains("..")) {
            throw new IllegalArgumentException("Path traversal not allowed");
        }

        int depth = countDepth(normalized);
        if (depth > maxDepth) {
            throw new IllegalArgumentException("Path depth exceeds maximum (" + maxDepth + ")");
        }
    }

    public void validatePathForStart(String path, String basePath) {
        validatePath(path);

        if (basePath != null && !basePath.isEmpty()) {
            String normalizedBase = normalizePath(basePath);
            String normalizedPath = normalizePath(path);

            if (!normalizedPath.startsWith(normalizedBase)) {
                throw new IllegalArgumentException("Path must be within base directory");
            }
        }
    }

    public String sanitizeFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String normalizePath(String path) {
        if (path == null) return "";

        String normalized = path.replace("\\", "/");

        normalized = normalized.replaceAll("/+", "/");

        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private int countDepth(String path) {
        if (path == null || path.isEmpty()) return 0;
        if (path.equals("/")) return 0;

        int count = 0;
        int idx = 0;
        while (idx < path.length()) {
            int slashIdx = path.indexOf('/', idx);
            if (slashIdx == -1) {
                if (idx < path.length()) count++;
                break;
            }
            if (slashIdx > idx) count++;
            idx = slashIdx + 1;
        }

        return count;
    }
}
