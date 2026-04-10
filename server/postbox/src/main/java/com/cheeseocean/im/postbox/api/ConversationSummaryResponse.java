package com.cheeseocean.im.postbox.api;

import com.cheeseocean.im.common.api.enums.ConversationKind;
import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import lombok.Data;

/**
 * CheeseBox 会话列表响应。
 *
 * @author xxxcrel
 */
@Data
public class ConversationSummaryResponse {

    private String conversationId;
    private ConversationKind kind;
    private String title;
    private String subtitle;
    private String lastMessagePreview;
    private MessagePreviewType lastMessagePreviewType;
    private int unreadCount;
    private Long lastMessageTime;
    private boolean notification;
}
