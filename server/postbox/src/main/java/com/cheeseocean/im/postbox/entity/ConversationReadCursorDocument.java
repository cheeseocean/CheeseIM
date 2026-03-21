package com.cheeseocean.im.postbox.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("im_conversation_read_cursor")
public class ConversationReadCursorDocument {

    @Id
    private String id;
    private String userId;
    private String conversationId;
    private Long readSeq;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
