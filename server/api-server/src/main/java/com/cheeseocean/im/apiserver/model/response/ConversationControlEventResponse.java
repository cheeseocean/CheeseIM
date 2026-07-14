package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

/** 客户端控制事件同步项。 */
@Data
public class ConversationControlEventResponse {
    private String eventId;
    private long cursor;
    private String conversationId;
    private int type;
    private String payload;
    private long createdAt;
    private long expiresAt;
}
