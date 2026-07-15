package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import com.cheeseocean.im.common.core.history.document.MessageSlot;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 历史消息预览解析器。
 *
 * @author xxxcrel
 */
@Component
public class MessagePreviewResolver {

    public Preview resolve(MessageSlot slot, String viewerUserId) {
        if (slot == null) {
            return new Preview(null, null);
        }
        Integer contentType = slot.getContentType();
        if (contentType != null) {
            if (contentType == ContentType.SYSTEM_NOTIFY.getCode()) {
                return new Preview("系统通知", MessagePreviewType.SYSTEM);
            }
            if (contentType == ContentType.REVOKE_NOTIFY.getCode()) {
                String text = viewerUserId != null && viewerUserId.equals(slot.getSenderId())
                        ? "你撤回了一条消息"
                        : "对方撤回了一条消息";
                return new Preview(text, MessagePreviewType.REVOKE);
            }
            if (contentType == ContentType.READ_RECEIPT.getCode()) {
                return new Preview("[已读回执]", MessagePreviewType.READ_RECEIPT);
            }
        }
        return new Preview(normalizeContent(slot.getContent()), MessagePreviewType.TEXT);
    }

    public static String normalizeContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(content);
    }

    public record Preview(String text, MessagePreviewType type) {
    }
}
