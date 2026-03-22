package com.cheeseocean.im.postbox.history;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("message_block")
public class MessageBlockDoc {

    @Id
    private String id;
    private String conversationId;
    private Long blockNo;
    private Long startSeq;
    private Long endSeq;
    private List<MessageSlot> messages = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Long getBlockNo() {
        return blockNo;
    }

    public void setBlockNo(Long blockNo) {
        this.blockNo = blockNo;
    }

    public Long getStartSeq() {
        return startSeq;
    }

    public void setStartSeq(Long startSeq) {
        this.startSeq = startSeq;
    }

    public Long getEndSeq() {
        return endSeq;
    }

    public void setEndSeq(Long endSeq) {
        this.endSeq = endSeq;
    }

    public List<MessageSlot> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageSlot> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
