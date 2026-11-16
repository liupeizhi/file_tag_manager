package com.filemanager.util;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.util.function.Supplier;

/**
 * 带 Range 支持的响应构建。
 * 浏览器播放音视频时会发起 Range 请求, 返回 206 才能拖动进度条。
 */
public final class RangedResponse {

    private RangedResponse() {
    }

    /**
     * 解析 Range 请求头。
     *
     * @return {start, end}; end 为 null 表示到文件末尾; 返回 null 表示按完整文件处理
     */
    public static Long[] parseRange(String rangeHeader, Long totalSize) {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return null;
        }
        String spec = rangeHeader.substring("bytes=".length()).trim();
        if (spec.contains(",")) {
            return null; // 多区间暂不支持
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        try {
            String startPart = spec.substring(0, dash).trim();
            String endPart = spec.substring(dash + 1).trim();

            if (startPart.isEmpty()) {
                // bytes=-N 表示最后 N 字节
                if (endPart.isEmpty() || totalSize == null) {
                    return null;
                }
                long suffix = Long.parseLong(endPart);
                long start = Math.max(0, totalSize - suffix);
                return new Long[] { start, totalSize - 1 };
            }

            long start = Long.parseLong(startPart);
            if (start < 0) {
                return null;
            }
            if (endPart.isEmpty()) {
                return new Long[] { start, null };
            }
            long end = Long.parseLong(endPart);
            if (end < start) {
                return null;
            }
            if (totalSize != null && end > totalSize - 1) {
                end = totalSize - 1;
            }
            return new Long[] { start, end };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构建支持 Range 的响应。
     *
     * @param rangeHeader    客户端 Range 头, 可为空
     * @param totalSize      资源总大小, 未知时传 null
     * @param streamSupplier 按需打开数据流的工厂(区间请求与完整请求走不同分支)
     */
    public static ResponseEntity<Resource> build(
            String rangeHeader,
            Long totalSize,
            MediaType contentType,
            String disposition,
            String encodedFilename,
            Supplier<InputStream> streamSupplier) {

        Long[] range = parseRange(rangeHeader, totalSize);

        if (range != null && totalSize != null && range[0] >= totalSize) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + totalSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .build();
        }

        InputStream stream = streamSupplier.get();

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + encodedFilename + "\"");

        if (range != null && totalSize != null) {
            long start = range[0];
            long end = range[1] != null ? range[1] : totalSize - 1;
            long length = end - start + 1;
            builder = ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalSize)
                    .contentLength(length);
        } else if (totalSize != null) {
            builder.contentLength(totalSize);
        }

        return builder.body(new InputStreamResource(stream));
    }
}
