package com.cheeseocean.im.apiserver.model.response;

import com.cheeseocean.im.common.api.enums.ConversationKind;
import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import lombok.Data;

@Data
public class ConversationResponse {
    private String ownerUserId;
    private String conversationId;
    private int conversationType;
    private String targetId;
    private int receiveOpt;
    private int unreadCount;
    private boolean pinned;
    private String attachedInfo;
    private int groupAtType;
    private boolean autoCleanup;
    private long cleanupCycle;
    private long latestCleanupTime;
    private long createdAt;
    private long updatedAt;
    private ConversationKind kind;
    private String title;
    private String subtitle;
    private String lastMessagePreview;
    private MessagePreviewType lastMessagePreviewType;
    private long lastMessageTime;
    private boolean notification;
}
