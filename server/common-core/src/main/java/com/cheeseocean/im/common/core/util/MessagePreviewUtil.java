package com.cheeseocean.im.common.core.util;

import com.cheeseocean.im.common.core.constants.MessageDisplayConstants;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import org.springframework.util.StringUtils;

import java.util.Map;

public final class MessagePreviewUtil {

    private MessagePreviewUtil() {
    }

    public static String resolvePreview(Integer contentType, String content, Map<String, String> ext) {
        ContentType type = resolveContentType(contentType);
        if (type != null) {
            return switch (type) {
                case READ_RECEIPT -> "[已读回执]";
                case REVOKE_NOTIFY -> "你撤回了一条消息";
                case TYPING -> null;
                case SYSTEM_NOTIFY -> MessageDisplayConstants.PREVIEW_SYSTEM_NOTIFICATION;
                case FORCE_LOGOUT -> MessageDisplayConstants.PREVIEW_SECURITY_ALERT;
                default -> fallbackPreview(content, ext);
            };
        }
        return fallbackPreview(content, ext);
    }

    public static MessagePreviewType resolvePreviewType(Integer contentType, boolean notification) {
        ContentType type = resolveContentType(contentType);
        if (type != null) {
            return switch (type) {
                case READ_RECEIPT -> MessagePreviewType.READ_RECEIPT;
                case REVOKE_NOTIFY -> MessagePreviewType.REVOKE;
                case TYPING -> MessagePreviewType.HIDDEN;
                case SYSTEM_NOTIFY -> MessagePreviewType.SYSTEM;
                case FORCE_LOGOUT -> MessagePreviewType.SECURITY;
                default -> notification ? MessagePreviewType.NOTIFICATION : MessagePreviewType.TEXT;
            };
        }
        return notification ? MessagePreviewType.NOTIFICATION : MessagePreviewType.TEXT;
    }

    private static ContentType resolveContentType(Integer contentType) {
        if (contentType == null) {
            return null;
        }
        try {
            return ContentType.fromCode(contentType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String fallbackPreview(String content, Map<String, String> ext) {
        if (StringUtils.hasText(content)) {
            return content;
        }
        if (ext != null && !ext.isEmpty()) {
            return MessageDisplayConstants.PREVIEW_ATTACHMENT;
        }
        return MessageDisplayConstants.PREVIEW_UNSUPPORTED;
    }
}
