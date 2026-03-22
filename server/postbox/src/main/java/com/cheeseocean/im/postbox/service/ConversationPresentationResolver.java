package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.constants.MessageDisplayConstants;
import com.cheeseocean.im.common.core.enums.ConversationKind;
import org.springframework.stereotype.Component;

@Component
public class ConversationPresentationResolver {

    public ConversationKind resolveKind(String conversationId) {
        if (conversationId != null) {
            if (conversationId.startsWith("c1:")) {
                return ConversationKind.DIRECT;
            }
            if (conversationId.startsWith("c2:")) {
                return ConversationKind.GROUP;
            }
            if (conversationId.startsWith("c3:")) {
                return ConversationKind.NOTIFICATION;
            }
            if (conversationId.startsWith("c4:")) {
                return ConversationKind.CHANNEL;
            }
        }
        return ConversationKind.DIRECT;
    }

    public String resolveTitle(String conversationId, String currentUserId) {
        String[] parts = conversationId == null ? new String[0] : conversationId.split(":");
        ConversationKind kind = resolveKind(conversationId);
        if (kind == ConversationKind.DIRECT && parts.length == 3) {
            return currentUserId != null && currentUserId.equals(parts[1]) ? parts[2] : parts[1];
        }
        if (kind == ConversationKind.NOTIFICATION) {
            return MessageDisplayConstants.CONVERSATION_TITLE_SYSTEM_NOTIFICATIONS;
        }
        if (parts.length >= 2) {
            return parts[1];
        }
        return conversationId;
    }

    public String resolveSubtitle(ConversationKind kind) {
        return switch (kind) {
            case DIRECT -> MessageDisplayConstants.CONVERSATION_SUBTITLE_DIRECT;
            case GROUP -> MessageDisplayConstants.CONVERSATION_SUBTITLE_GROUP;
            case NOTIFICATION -> MessageDisplayConstants.CONVERSATION_SUBTITLE_NOTIFICATION;
            case CHANNEL -> MessageDisplayConstants.CONVERSATION_SUBTITLE_CHANNEL;
        };
    }
}
