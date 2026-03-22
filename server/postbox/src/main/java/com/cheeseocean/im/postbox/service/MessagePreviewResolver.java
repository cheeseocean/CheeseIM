package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.enums.MessagePreviewType;
import com.cheeseocean.im.common.core.util.MessagePreviewUtil;
import com.cheeseocean.im.postbox.history.MessageSlot;
import org.springframework.stereotype.Component;

@Component
public class MessagePreviewResolver {

    public String resolvePreview(MessageSlot message) {
        if (message == null) {
            return null;
        }
        return MessagePreviewUtil.resolvePreview(message.getContentType(), message.getContent(), message.getExt());
    }

    public MessagePreviewType resolvePreviewType(MessageSlot message) {
        if (message == null) {
            return null;
        }
        boolean notification = message.getOptions() != null && Boolean.TRUE.equals(message.getOptions().isNotification());
        return MessagePreviewUtil.resolvePreviewType(message.getContentType(), notification);
    }
}
