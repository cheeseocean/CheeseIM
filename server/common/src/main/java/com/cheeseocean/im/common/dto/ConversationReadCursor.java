package com.cheeseocean.im.common.dto;

import java.io.Serializable;

public class ConversationReadCursor implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private String conversationId;
    private Long readSeq;
    private Long updatedAt;

    public static ConversationReadCursor of(String userId, String conversationId, Long readSeq, Long updatedAt) {
        ConversationReadCursor cursor = new ConversationReadCursor();
        cursor.setUserId(userId);
        cursor.setConversationId(conversationId);
        cursor.setReadSeq(readSeq);
        cursor.setUpdatedAt(updatedAt);
        return cursor;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Long getReadSeq() {
        return readSeq;
    }

    public void setReadSeq(Long readSeq) {
        this.readSeq = readSeq;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
