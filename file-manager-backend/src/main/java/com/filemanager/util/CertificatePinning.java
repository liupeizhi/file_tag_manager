package com.filemanager.util;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLContext;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自签名证书支持：NAS 等设备常使用 CN 不含实际访问地址的自签名证书，
 * 默认的 PKIX 校验与主机名校验都会失败。
 *
 * 策略：证书链仍按 JDK 信任库完整校验（自签名证书需先导入信任库），
 * 仅当对端证书指纹命中配置的白名单时，才放宽主机名校验。
 */
@Component
public class CertificatePinning {

    private static final Logger log = LoggerFactory.getLogger(CertificatePinning.class);

    private final HostnameVerifier defaultVerifier = new DefaultHostnameVerifier();

    private final List<String> pinnedFingerprints = new ArrayList<>();

    public CertificatePinning(@Value("${app.webdav.pinned-certificates:}") List<String> fingerprints) {
        for (String fingerprint : fingerprints) {
            if (fingerprint != null && !fingerprint.isBlank()) {
                pinnedFingerprints.add(normalize(fingerprint));
            }
        }
        if (!pinnedFingerprints.isEmpty()) {
            log.info("已加载 {} 个 WebDAV 证书指纹白名单", pinnedFingerprints.size());
        }
    }

    public SSLConnectionSocketFactory socketFactory() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        return new SSLConnectionSocketFactory(context, hostnameVerifier());
    }

    public HostnameVerifier hostnameVerifier() {
        if (pinnedFingerprints.isEmpty()) {
            return defaultVerifier;
        }
        return (hostname, session) -> {
            if (defaultVerifier.verify(hostname, session)) {
                return true;
            }
            try {
                Certificate[] chain = session.getPeerCertificates();
                if (chain.length == 0) {
                    return false;
                }
                String fingerprint = fingerprint(chain[0]);
                if (pinnedFingerprints.contains(normalize(fingerprint))) {
                    log.debug("主机名校验失败但证书指纹命中白名单: {}", fingerprint);
                    return true;
                }
                log.warn("证书指纹不在白名单中, 拒绝连接: {}", fingerprint);
            } catch (Exception e) {
                log.warn("读取对端证书失败: {}", e.getMessage());
            }
            return false;
        };
    }

    private String fingerprint(Certificate certificate) throws Exception {
        if (!(certificate instanceof X509Certificate)) {
            return "";
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(certificate.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private String normalize(String fingerprint) {
        return fingerprint.replaceAll("[^0-9a-fA-F]", "").toUpperCase(Locale.ROOT);
    }
}
