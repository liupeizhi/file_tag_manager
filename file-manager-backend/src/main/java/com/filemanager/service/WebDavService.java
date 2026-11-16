package com.filemanager.service;

import com.filemanager.entity.ServerConfig;
import com.filemanager.dto.FileResource;
import com.filemanager.exception.ProtocolException;
import com.filemanager.util.CertificatePinning;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.DavResource;
import com.github.sardine.impl.SardineImpl;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WebDavService implements FileProtocolService {

    private static final Logger log = LoggerFactory.getLogger(WebDavService.class);

    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int SOCKET_TIMEOUT_MS = 30000;

    @Autowired
    private CertificatePinning certificatePinning;

    public Sardine createSardine(String username, String password) {
        System.setProperty("http.proxyHost", "");
        System.setProperty("http.proxyPort", "");
        System.setProperty("https.proxyHost", "");
        System.setProperty("https.proxyPort", "");
        System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]");
        System.setProperty("socksProxyHost", "");
        System.setProperty("socksProxyPort", "");
        System.setProperty("java.net.socks.useDynamicProxy", "false");

        try {
            HttpClientBuilder builder = HttpClients.custom()
                    .setSSLSocketFactory(certificatePinning.socketFactory())
                    .setDefaultCredentialsProvider(new BasicCredentialsProvider());
            Sardine sardine = new SardineImpl(builder, username, password);
            sardine.enablePreemptiveAuthentication("http");
            sardine.enablePreemptiveAuthentication("https");
            return sardine;
        } catch (Exception e) {
            log.warn("构建 TLS 客户端失败, 回退到默认配置: {}", e.getMessage());
            Sardine sardine = SardineFactory.begin(username, password);
            sardine.enablePreemptiveAuthentication("http");
            sardine.enablePreemptiveAuthentication("https");
            return sardine;
        }
    }
    
    @Override
    public boolean testConnection(ServerConfig server) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            // 用 PROPFIND(Depth:0) 而不是 Sardine 的 exists():
            // exists() 内部走 HTTP HEAD, 多数 WebDAV 服务不实现 HEAD(返回 405),
            // 会导致明明可用也判定为连接失败。
            return withHttpsFallback(server.getUrl(), base -> {
                List<DavResource> resources = sardine.list(base, 0);
                return resources != null;
            });
        } catch (Exception e) {
            log.warn("WebDAV 连接测试失败 [{} {}]: {}", server.getName(), server.getUrl(), e.getMessage());
            return false;
        }
    }
    
    private String encodePath(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] parts = path.split("/", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) result.append("/");
            if (!parts[i].isEmpty()) {
                result.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
        return result.toString();
    }
    
    @Override
    public List<FileResource> listFiles(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            return withHttpsFallback(server.getUrl(), base -> {
                String url = base + encodePath(path);
                List<DavResource> resources = sardine.list(url);

                return resources.stream()
                        .map(this::toFileResource)
                        .collect(Collectors.toList());
            });
        } catch (IOException e) {
            throw new ProtocolException("webdav", "listFiles", e.getMessage());
        }
    }

    /**
     * 部分 WebDAV 服务只监听 HTTPS, 用 http:// 访问会返回 301 重定向,
     * 而 Sardine 不会自动跟随重定向 —— 遇到 3xx 时自动改用 https:// 重试一次。
     */
    private <T> T withHttpsFallback(String baseUrl, IoFunction<T> action) throws IOException {
        try {
            return action.apply(baseUrl);
        } catch (ProtocolException e) {
            if (shouldRetryWithHttps(baseUrl, e.getMessage())) {
                // 回退后若仍失败, 抛出 https 的真实错误(如 502), 原始 301 没有诊断价值
                return action.apply(httpsUrl(baseUrl));
            }
            throw e;
        } catch (IOException e) {
            // Sardine 直接抛 IOException, 301 也要在这里识别
            if (shouldRetryWithHttps(baseUrl, e.getMessage())) {
                return action.apply(httpsUrl(baseUrl));
            }
            throw e;
        }
    }

    private boolean shouldRetryWithHttps(String baseUrl, String message) {
        return baseUrl.startsWith("http://")
                && message != null
                && message.contains("Unexpected response (30");
    }

    private String httpsUrl(String baseUrl) {
        return "https://" + baseUrl.substring("http://".length());
    }

    @FunctionalInterface
    private interface IoFunction<T> {
        T apply(String url) throws IOException;
    }
    
    private FileResource toFileResource(DavResource resource) {
        FileResource fr = new FileResource();
        fr.setPath(resource.getPath());
        fr.setName(getNameFromPath(resource.getPath()));
        fr.setDirectory(resource.isDirectory());
        fr.setSize(resource.getContentLength() != null ? resource.getContentLength() : 0);
        fr.setContentType(resource.getContentType());
        if (resource.getModified() != null) {
            fr.setLastModified(LocalDateTime.ofInstant(resource.getModified().toInstant(), ZoneId.systemDefault()));
        }
        return fr;
    }
    
    private String getNameFromPath(String path) {
        if (path == null || path.isEmpty()) return "";
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }
    
    @Override
    public InputStream downloadFile(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            return withHttpsFallback(server.getUrl(), base -> sardine.get(base + encodePath(path)));
        } catch (IOException e) {
            throw new ProtocolException("webdav", "downloadFile", e.getMessage());
        }
    }

    /**
     * 带 Range 头的下载 —— WebDAV 服务端会返回 206 Partial Content,
     * 浏览器才能对音视频做进度条拖动与分段缓冲。
     */
    @Override
    public InputStream downloadFile(ServerConfig server, String path, Long start, Long end) {
        if (start == null) {
            return downloadFile(server, path);
        }
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            Map<String, String> headers = new HashMap<>();
            headers.put("Range", "bytes=" + start + "-" + (end != null ? end : ""));
            return sardine.get(url, headers);
        } catch (IOException e) {
            throw new ProtocolException("webdav", "downloadFile", e.getMessage());
        }
    }
    
    @Override
    public void uploadFile(ServerConfig server, String path, InputStream data) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            byte[] bytes = data.readAllBytes();
            sardine.put(url, bytes);
        } catch (IOException e) {
            throw new ProtocolException("webdav", "uploadFile", e.getMessage());
        }
    }
    
    @Override
    public void createDirectory(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            sardine.createDirectory(url);
        } catch (IOException e) {
            throw new ProtocolException("webdav", "createDirectory", e.getMessage());
        }
    }
    
    @Override
    public void delete(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String url = server.getUrl() + encodePath(path);
            sardine.delete(url);
        } catch (IOException e) {
            throw new ProtocolException("webdav", "delete", e.getMessage());
        }
    }
    
    @Override
    public void move(ServerConfig server, String from, String to) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String fromUrl = server.getUrl() + encodePath(from);
            String toUrl = server.getUrl() + encodePath(to);
            sardine.move(fromUrl, toUrl);
        } catch (IOException e) {
            throw new ProtocolException("webdav", "move", e.getMessage());
        }
    }
    
    @Override
    public FileResource getFileInfo(ServerConfig server, String path) {
        try {
            Sardine sardine = createSardine(server.getUsername(), server.getPassword());
            String encodedPath = encodePath(path);

            return withHttpsFallback(server.getUrl(), base -> {
                List<DavResource> resources = sardine.list(base + encodedPath, 0);
                if (resources != null && !resources.isEmpty()) {
                    DavResource resource = resources.stream()
                            .filter(r -> r.getPath().equals(encodedPath) || r.getPath().endsWith(encodedPath))
                            .findFirst()
                            .orElse(resources.get(0));
                    return toFileResource(resource);
                }
                return null;
            });
        } catch (IOException e) {
            return null;
        }
    }
}