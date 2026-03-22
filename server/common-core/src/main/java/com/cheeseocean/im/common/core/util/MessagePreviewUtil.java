package com.cheeseocean.im.common.core.util;

import com.cheeseocean.im.common.core.constants.MessageConstants;
import com.cheeseocean.im.common.core.constants.MessageDisplayConstants;
import com.cheeseocean.im.common.core.enums.MessagePreviewType;
import org.springframework.util.StringUtils;

import java.util.Map;

public final class MessagePreviewUtil {

    private MessagePreviewUtil() {
    }

    public static String resolvePreview(Integer contentType, String content, Map<String, String> ext) {
        if (contentType != null) {
            return switch (contentType) {
                case MessageConstants.CONTENT_TYPE_READ_RECEIPT -> "[已读回执]";
                case MessageConstants.CONTENT_TYPE_REVOKE_NOTIFY -> "你撤回了一条消息";
                case MessageConstants.CONTENT_TYPE_TYPING -> null;
                case MessageConstants.CONTENT_TYPE_SYSTEM_NOTIFY -> MessageDisplayConstants.PREVIEW_SYSTEM_NOTIFICATION;
                case MessageConstants.CONTENT_TYPE_FORCE_LOGOUT -> MessageDisplayConstants.PREVIEW_SECURITY_ALERT;
                default -> fallbackPreview(content, ext);
            };
        }
        return fallbackPreview(content, ext);
    }

    public static MessagePreviewType resolvePreviewType(Integer contentType, boolean notification) {
        if (contentType != null) {
            return switch (contentType) {
                case MessageConstants.CONTENT_TYPE_READ_RECEIPT -> MessagePreviewType.READ_RECEIPT;
                case MessageConstants.CONTENT_TYPE_REVOKE_NOTIFY -> MessagePreviewType.REVOKE;
                case MessageConstants.CONTENT_TYPE_TYPING -> MessagePreviewType.HIDDEN;
                case MessageConstants.CONTENT_TYPE_SYSTEM_NOTIFY -> MessagePreviewType.SYSTEM;
                case MessageConstants.CONTENT_TYPE_FORCE_LOGOUT -> MessagePreviewType.SECURITY;
                default -> notification ? MessagePreviewType.NOTIFICATION : MessagePreviewType.TEXT;
            };
        }
        return notification ? MessagePreviewType.NOTIFICATION : MessagePreviewType.TEXT;
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
