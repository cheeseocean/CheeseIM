package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.postbox.config.AttachmentDownloadProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class AttachmentDownloadTokenService {

    private final AttachmentDownloadProperties properties;

    public AttachmentDownloadTokenService(AttachmentDownloadProperties properties) {
        this.properties = properties;
    }

    public String issueToken(String attachmentId) {
        long expireAt = System.currentTimeMillis() + properties.getTokenTtlSeconds() * 1000L;
        String payload = attachmentId + "." + expireAt;
        String signature = sign(payload);
        return base64Url(payload) + "." + signature;
    }

    public long resolveExpireAt() {
        return System.currentTimeMillis() + properties.getTokenTtlSeconds() * 1000L;
    }

    public boolean isValid(String attachmentId, String token) {
        if (attachmentId == null || token == null || token.isBlank()) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return false;
        }
        String payload = decodeBase64Url(parts[0]);
        if (payload == null) {
            return false;
        }
        String expectedSignature = sign(payload);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        int separator = payload.lastIndexOf('.');
        if (separator <= 0 || separator >= payload.length() - 1) {
            return false;
        }
        String tokenAttachmentId = payload.substring(0, separator);
        long expireAt;
        try {
            expireAt = Long.parseLong(payload.substring(separator + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        return attachmentId.equals(tokenAttachmentId) && expireAt > System.currentTimeMillis();
    }

    public String buildDownloadUrl(String attachmentId, String token) {
        String base = properties.getPublicBaseUrl();
        String path = "/api/im/attachments/" + attachmentId + "/download?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        if (base == null || base.isBlank()) {
            return path;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getTokenSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("attachment token signing failed", e);
        }
    }

    private String base64Url(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String decodeBase64Url(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
