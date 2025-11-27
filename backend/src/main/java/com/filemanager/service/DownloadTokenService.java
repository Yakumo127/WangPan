package com.filemanager.service;

import com.filemanager.entity.DownloadTokenUsed;
import com.filemanager.repository.DownloadTokenUsedRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

/**
 * 下载一次性 Token 生成与校验服务。
 *
 * 设计要点：
 * - Token 内包含 fileId / userId / exp / admin / nonce 等字段；
 * - 使用 HMAC-SHA256 签名，防止伪造与篡改；
 * - 通过 download_tokens_used 表记录 tokenHash，实现“单次使用”限制。
 */
@Service
public class DownloadTokenService {

    private final DownloadTokenUsedRepository usedRepository;

    @Value("${download.token.secret:enterpriseFileManagerDownloadSecretKey2024}")
    private String secret;

    @Value("${download.token.ttl-seconds:10}")
    private long ttlSeconds;

    // 下载限速（字节/秒），<=0 表示不限速，默认 3MB/s
    @Value("${download.rate-limit-bytes-per-second:3145728}")
    private long rateLimitBytesPerSecond;

    public DownloadTokenService(DownloadTokenUsedRepository usedRepository) {
        this.usedRepository = usedRepository;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public long getRateLimitBytesPerSecond() {
        return rateLimitBytesPerSecond;
    }

    /**
     * 为普通用户生成下载 Token。
     */
    public String generateUserToken(Long fileId, Long userId) {
        return generateToken(fileId, userId, false);
    }

    /**
     * 为管理员生成下载 Token（允许跨用户下载）。
     */
    public String generateAdminToken(Long fileId, Long userId) {
        return generateToken(fileId, userId, true);
    }

    private String generateToken(Long fileId, Long userId, boolean admin) {
        if (fileId == null || userId == null) {
            throw new IllegalArgumentException("生成下载链接时 fileId 或 userId 为空");
        }
        long now = System.currentTimeMillis();
        long exp = now + ttlSeconds * 1000L;
        String nonce = UUID.randomUUID().toString();
        String payloadJson = String.format(
                "{\"fileId\":%d,\"userId\":%d,\"admin\":%s,\"nonce\":\"%s\",\"exp\":%d}",
                fileId, userId, admin ? "true" : "false", escapeJson(nonce), exp
        );
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signatureBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmacSha256(payloadBase64.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8)));
        return payloadBase64 + "." + signatureBase64;
    }

    /**
     * 解析并校验 Token，包括签名与过期时间。
     * 若校验失败或已过期，将抛出 IllegalArgumentException。
     */
    public DecodedToken parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("下载链接无效：缺少 token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("下载链接无效：格式错误");
        }
        String payloadBase64 = parts[0];
        String sigBase64 = parts[1];

        byte[] expectedSig = hmacSha256(payloadBase64.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8));
        byte[] actualSig;
        try {
            actualSig = Base64.getUrlDecoder().decode(sigBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("下载链接无效：签名格式错误");
        }
        if (!constantTimeEquals(expectedSig, actualSig)) {
            throw new IllegalArgumentException("下载链接无效：签名校验失败");
        }

        String payloadJson;
        try {
            payloadJson = new String(Base64.getUrlDecoder().decode(payloadBase64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("下载链接无效：载荷解析失败");
        }

        DecodedToken decoded = DecodedToken.fromJson(payloadJson);
        long now = System.currentTimeMillis();
        if (decoded.exp <= now) {
            throw new IllegalArgumentException("下载链接已过期，请重新发起下载");
        }
        return decoded;
    }

    /**
     * 标记 token 为已使用。若已使用过，则抛出 IllegalStateException。
     */
    public void assertNotUsedAndMarkUsed(String token) {
        String hash = sha256Hex(token);
        DownloadTokenUsed used = new DownloadTokenUsed();
        used.setTokenHash(hash);
        used.setUsedAt(LocalDateTime.now());
        try {
            usedRepository.save(used);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("下载链接已被使用，请重新发起下载", ex);
        }
    }

    private byte[] hmacSha256(byte[] data, byte[] key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("计算下载 token 签名失败", e);
        }
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) sb.append('0');
                sb.append(h);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算 token 哈希失败", e);
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class DecodedToken {
        public Long fileId;
        public Long userId;
        public boolean admin;
        public String nonce;
        public long exp;

        public static DecodedToken fromJson(String json) {
            if (json == null || json.isBlank()) {
                throw new IllegalArgumentException("下载链接无效：载荷为空");
            }
            DecodedToken t = new DecodedToken();
            // 简单 JSON 解析（字段固定，避免引入完整 JSON 依赖）
            try {
                String cleaned = json.trim();
                if (cleaned.startsWith("{")) cleaned = cleaned.substring(1);
                if (cleaned.endsWith("}")) cleaned = cleaned.substring(0, cleaned.length() - 1);
                String[] parts = cleaned.split(",");
                for (String part : parts) {
                    String[] kv = part.split(":", 2);
                    if (kv.length != 2) continue;
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    if (key.startsWith("\"")) key = key.substring(1);
                    if (key.endsWith("\"")) key = key.substring(0, key.length() - 1);
                    switch (key) {
                        case "fileId" -> t.fileId = Long.parseLong(value);
                        case "userId" -> t.userId = Long.parseLong(value);
                        case "admin" -> t.admin = "true".equalsIgnoreCase(value);
                        case "nonce" -> {
                            if (value.startsWith("\"")) value = value.substring(1);
                            if (value.endsWith("\"")) value = value.substring(0, value.length() - 1);
                            t.nonce = value.replace("\\\"", "\"").replace("\\\\", "\\");
                        }
                        case "exp" -> t.exp = Long.parseLong(value);
                        default -> {
                        }
                    }
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("下载链接无效：载荷解析失败", e);
            }
            if (t.fileId == null || t.userId == null || t.exp <= 0) {
                throw new IllegalArgumentException("下载链接无效：缺少必要字段");
            }
            return t;
        }

        public Instant getExpiryInstant() {
            return Instant.ofEpochMilli(exp);
        }

        public LocalDateTime getExpiryTime() {
            return LocalDateTime.ofInstant(getExpiryInstant(), ZoneId.systemDefault());
        }
    }
}
