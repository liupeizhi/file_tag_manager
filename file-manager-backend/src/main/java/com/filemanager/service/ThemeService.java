package com.filemanager.service;

import com.filemanager.dto.ThemeDTO;
import com.filemanager.entity.User;
import com.filemanager.entity.UserTheme;
import com.filemanager.exception.ThemeException;
import com.filemanager.repository.UserRepository;
import com.filemanager.repository.UserThemeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ThemeService {

    private static final int MIN_FONT_SIZE = 12;
    private static final int MAX_FONT_SIZE = 20;
    private static final long MAX_BACKGROUND_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif");

    @Autowired
    private UserThemeRepository userThemeRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.theme.background-path:${user.home}/.file-manager/backgrounds}")
    private String backgroundPath;

    public ThemeDTO getTheme() {
        UserTheme theme = loadOrCreate(currentUserId());
        return toDTO(theme);
    }

    @Transactional
    public ThemeDTO updateTheme(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new ThemeException("Invalid theme settings");
        }

        UserTheme theme = loadOrCreate(currentUserId());

        // 显式传 null 表示"清除自定义", 因此必须区分"字段缺失"与"字段为 null"
        if (request.has("fontSize")) {
            JsonNode fontSize = request.get("fontSize");
            if (fontSize.isNull()) {
                theme.setFontSize(14);
            } else {
                theme.setFontSize(validateFontSize(fontSize.asInt()));
            }
        }
        if (request.has("brightness")) {
            theme.setBrightness(validateChoice(request.get("brightness"), "Brightness",
                    Set.of("system", "light", "dark")));
        }
        if (request.has("style")) {
            theme.setStyle(validateChoice(request.get("style"), "Style",
                    Set.of("ink", "comic")));
        }
        if (request.has("textColor")) {
            theme.setTextColor(validateColor(request.get("textColor"), "Text color"));
        }
        if (request.has("accentColor")) {
            theme.setAccentColor(validateColor(request.get("accentColor"), "Accent color"));
        }

        return toDTO(userThemeRepository.save(theme));
    }

    /**
     * 校验枚举型取值, null 表示清除(回落默认值)。
     */
    private String validateChoice(JsonNode node, String label, Set<String> allowed) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText().trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (!allowed.contains(value)) {
            throw new ThemeException(label + " must be one of " + allowed);
        }
        return value;
    }

    @Transactional
    public ThemeDTO uploadBackground(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ThemeException("No background image was provided");
        }
        if (file.getSize() > MAX_BACKGROUND_BYTES) {
            throw new ThemeException("Background image exceeds the 10 MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ThemeException("Unsupported image type: " + contentType);
        }

        UserTheme theme = loadOrCreate(currentUserId());

        String extension = extensionFor(contentType);
        String storedName = currentUserId() + "-" + UUID.randomUUID() + extension;

        try {
            Path dir = Paths.get(backgroundPath).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName).normalize();
            if (!target.startsWith(dir)) {
                throw new ThemeException("Invalid background filename");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ThemeException("Failed to store background image: " + e.getMessage());
        }

        deleteBackgroundFile(theme.getBackgroundFilename());
        theme.setBackgroundFilename(storedName);
        theme.setBackgroundOriginalName(file.getOriginalFilename());
        theme.setBackgroundMime(contentType.toLowerCase(Locale.ROOT));

        return toDTO(userThemeRepository.save(theme));
    }

    @Transactional
    public ThemeDTO removeBackground() {
        UserTheme theme = loadOrCreate(currentUserId());
        deleteBackgroundFile(theme.getBackgroundFilename());
        theme.setBackgroundFilename(null);
        theme.setBackgroundOriginalName(null);
        theme.setBackgroundMime(null);
        return toDTO(userThemeRepository.save(theme));
    }

    public BackgroundFile readBackground() {
        UserTheme theme = loadOrCreate(currentUserId());
        if (theme.getBackgroundFilename() == null) {
            throw new ThemeException("Background image not found");
        }

        Path path = resolveBackgroundPath(theme.getBackgroundFilename());
        if (!Files.exists(path)) {
            throw new ThemeException("Background image not found");
        }

        try {
            return new BackgroundFile(
                    Files.readAllBytes(path),
                    theme.getBackgroundMime() != null ? theme.getBackgroundMime() : "application/octet-stream",
                    theme.getUpdatedAt() != null ? theme.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0L);
        } catch (IOException e) {
            throw new ThemeException("Failed to read background image: " + e.getMessage());
        }
    }

    private void deleteBackgroundFile(String filename) {
        if (filename == null || filename.isEmpty()) return;
        try {
            Files.deleteIfExists(resolveBackgroundPath(filename));
        } catch (IOException ignored) {
        }
    }

    private Path resolveBackgroundPath(String filename) {
        Path dir = Paths.get(backgroundPath).toAbsolutePath().normalize();
        Path path = dir.resolve(filename).normalize();
        if (!path.startsWith(dir)) {
            throw new ThemeException("Invalid background filename");
        }
        return path;
    }

    private int validateFontSize(Integer fontSize) {
        if (fontSize < MIN_FONT_SIZE || fontSize > MAX_FONT_SIZE) {
            throw new ThemeException("Font size must be between " + MIN_FONT_SIZE + " and " + MAX_FONT_SIZE);
        }
        return fontSize;
    }

    private String validateColor(JsonNode node, String label) {
        if (node == null || node.isNull()) return null;
        String trimmed = node.asText().trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return null;
        if (!trimmed.matches("^#[0-9a-f]{6}$") && !trimmed.matches("^#[0-9a-f]{3}$")) {
            throw new ThemeException(label + " must be a hex color");
        }
        return trimmed;
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".img";
        };
    }

    private UserTheme loadOrCreate(Long userId) {
        return userThemeRepository.findById(userId)
                .orElseGet(() -> {
                    UserTheme theme = new UserTheme();
                    theme.setUserId(userId);
                    theme.setFontSize(14);
                    return theme;
                });
    }

    private ThemeDTO toDTO(UserTheme theme) {
        ThemeDTO dto = new ThemeDTO();
        dto.setFontSize(theme.getFontSize());
        dto.setBrightness(theme.getBrightness());
        dto.setStyle(theme.getStyle());
        dto.setTextColor(theme.getTextColor());
        dto.setAccentColor(theme.getAccentColor());
        dto.setUpdatedAt(theme.getUpdatedAt() != null
                ? theme.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                : 0L);

        if (theme.getBackgroundFilename() != null) {
            dto.setBackground(new ThemeDTO.BackgroundDTO(
                    theme.getBackgroundOriginalName() != null ? theme.getBackgroundOriginalName() : "background",
                    theme.getBackgroundMime(),
                    dto.getUpdatedAt()));
        }
        return dto;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ThemeException("未登录");
        }
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ThemeException("用户不存在"));
        return user.getId();
    }

    public static class BackgroundFile {
        private final byte[] data;
        private final String mime;
        private final long updatedAt;

        public BackgroundFile(byte[] data, String mime, long updatedAt) {
            this.data = data;
            this.mime = mime;
            this.updatedAt = updatedAt;
        }

        public byte[] getData() { return data; }
        public String getMime() { return mime; }
        public long getUpdatedAt() { return updatedAt; }
    }
}
