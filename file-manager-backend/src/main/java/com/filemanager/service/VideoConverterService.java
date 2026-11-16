package com.filemanager.service;

import com.filemanager.entity.FileMetadata;
import com.filemanager.exception.VideoConversionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 服务端视频转码。
 *
 * 浏览器媒体管线只原生支持少数容器(MP4/WebM/M4V), AVI、RMVB、WMV、FLV、MOV 等
 * 连解复用器都没有, 因此必须先转成 MP4(H.264) 才能预览。
 *
 * 转换结果按内容指纹缓存到本地, 同一文件只需转一次。
 */
@Service
public class VideoConverterService {

    /**
     * 浏览器无法原生解码、需要转码的容器格式。
     */
    private static final Set<String> CONVERT_REQUIRED = Set.of(
            "avi", "rm", "rmvb", "wmv", "asf", "flv", "mov", "qt",
            "vob", "m4v", "mpg", "mpeg", "mpe", "3gp", "ogv", "f4v", "divx", "dat");

    private static final long FFMPEG_CHECK_TIMEOUT_SECONDS = 5;

    @Value("${app.video.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${app.video.cache-path:${user.home}/.file-manager/video-cache}")
    private String cachePath;

    @Value("${app.video.max-convert-seconds:300}")
    private int maxConvertSeconds;

    @Value("${app.video.max-size-bytes:2147483648}")
    private long maxSizeBytes;

    @Autowired
    private FileService fileService;

    private volatile Boolean ffmpegAvailable;

    /**
     * 该文件是否需要转码才能在浏览器里播放。
     */
    public boolean needsConversion(String path) {
        String ext = extension(path);
        return CONVERT_REQUIRED.contains(ext);
    }

    /**
     * 返回浏览器可播放的 MP4 缓存路径, 不存在则执行转码。
     */
    public Path getOrConvert(Long serverId, String path, FileMetadata metadata) {
        if (!isFfmpegAvailable()) {
            throw new VideoConversionException(
                    "该视频格式(" + extension(path).toUpperCase() + ")浏览器无法直接播放，"
                            + "需要服务器安装 ffmpeg 才能转码预览。");
        }

        Long size = metadata != null ? metadata.getSize() : null;
        if (size != null && size > maxSizeBytes) {
            throw new VideoConversionException(
                    "文件超过 " + (maxSizeBytes / 1024 / 1024) + "MB 转码上限，请下载后使用专业播放器打开。");
        }

        Path cached = cacheFileFor(serverId, path, metadata);
        try {
            if (Files.exists(cached) && Files.size(cached) > 0) {
                return cached;
            }
        } catch (IOException e) {
            // 缓存不可读时忽略, 重新转码
        }

        return convert(serverId, path, cached);
    }

    private Path convert(Long serverId, String path, Path target) {
        Path dir = Paths.get(cachePath).toAbsolutePath().normalize();
        Path temp;
        try {
            Files.createDirectories(dir);
            temp = Files.createTempFile(dir, "convert_", ".mp4");
        } catch (IOException e) {
            throw new VideoConversionException("无法创建转码缓存目录: " + e.getMessage());
        }

        boolean converted = false;
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-hide_banner",
                    "-loglevel", "error",
                    "-y",
                    "-i", "pipe:0",
                    "-c:v", "libx264",
                    "-preset", "veryfast",
                    "-crf", "23",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-movflags", "+faststart",
                    "-f", "mp4",
                    temp.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            process = pb.start();
            final Process ffmpeg = process;

            // 源文件流写入 ffmpeg stdin, 需并发执行, 否则管道写满会死锁
            InputStream source = fileService.downloadFile(serverId, path);
            Thread pump = new Thread(() -> {
                try (InputStream in = source; java.io.OutputStream out = ffmpeg.getOutputStream()) {
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                } catch (IOException ignored) {
                    // ffmpeg 提前退出时写入会失败, 交由进程退出码判断
                }
            }, "video-convert-pump");
            pump.setDaemon(true);
            pump.start();

            StringBuilder log = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (log.length() < 4000) {
                        log.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {
            }

            boolean finished = process.waitFor(maxConvertSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new VideoConversionException("视频转码超时（超过 " + maxConvertSeconds + " 秒）");
            }

            pump.join(5000);

            if (process.exitValue() != 0) {
                throw new VideoConversionException(
                        "视频转码失败: " + (log.toString().trim().isEmpty()
                                ? "ffmpeg exit " + process.exitValue() : log.toString().trim()));
            }

            if (!Files.exists(temp) || Files.size(temp) == 0) {
                throw new VideoConversionException("视频转码产出为空文件");
            }

            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            converted = true;
            return target;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoConversionException("视频转码被中断");
        } catch (VideoConversionException e) {
            throw e;
        } catch (IOException e) {
            throw new VideoConversionException("视频转码失败: " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            // 无论成功与否, 未移入缓存的临时文件都要清掉
            if (!converted) {
                deleteQuietly(temp);
            }
        }
    }

    /**
     * 缓存文件名 = sha256(serverId | path | lastModified | size), 内容变化会自动失效。
     */
    private Path cacheFileFor(Long serverId, String path, FileMetadata metadata) {
        StringBuilder key = new StringBuilder()
                .append(serverId).append('|')
                .append(path).append('|')
                .append(metadata != null && metadata.getLastModified() != null
                        ? metadata.getLastModified() : "")
                .append('|')
                .append(metadata != null && metadata.getSize() != null ? metadata.getSize() : "");

        String hash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(key.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            hash = sb.toString();
        } catch (Exception e) {
            hash = Integer.toHexString(key.toString().hashCode());
        }

        return Paths.get(cachePath).toAbsolutePath().normalize().resolve(hash + ".mp4");
    }

    public boolean isFfmpegAvailable() {
        if (ffmpegAvailable != null) {
            return ffmpegAvailable;
        }
        synchronized (this) {
            if (ffmpegAvailable != null) {
                return ffmpegAvailable;
            }
            boolean ok = false;
            try {
                Process process = new ProcessBuilder(ffmpegPath, "-version")
                        .redirectErrorStream(true)
                        .start();
                ok = process.waitFor(FFMPEG_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        && process.exitValue() == 0;
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            } catch (Exception e) {
                ok = false;
            }
            ffmpegAvailable = ok;
            return ok;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private String extension(String path) {
        if (path == null) {
            return "";
        }
        int idx = path.lastIndexOf('.');
        if (idx < 0 || idx == path.length() - 1) {
            return "";
        }
        return path.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}
