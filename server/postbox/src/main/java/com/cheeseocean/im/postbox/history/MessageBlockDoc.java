package com.cheeseocean.im.postbox.history;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史消息块文档。
 *
 * @author xxxcrel
 */
@Document("message_block")
@CompoundIndexes({
        @CompoundIndex(name = "idx_message_block_conversation_block", def = "{'conversationId': 1, 'blockNo': -1}")
})
public class MessageBlockDoc {

    @Id
    private String id;
    private String conversationId;
    private Long blockNo;
    private Long startSeq;
    private Long endSeq;
    @Field("messages")
    private Map<String, MessageSlot> messageMap = new LinkedHashMap<>();
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
        if (messageMap == null || messageMap.isEmpty()) {
            return new ArrayList<>();
        }
        int maxIndex = -1;
        for (String key : messageMap.keySet()) {
            try {
                maxIndex = Math.max(maxIndex, Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
        if (maxIndex < 0) {
            return new ArrayList<>();
        }
        List<MessageSlot> messages = new ArrayList<>(java.util.Collections.nCopies(maxIndex + 1, null));
        for (Map.Entry<String, MessageSlot> entry : messageMap.entrySet()) {
            try {
                messages.set(Integer.parseInt(entry.getKey()), entry.getValue());
            } catch (NumberFormatException ignored) {
            }
        }
        return messages;
    }

    public void setMessages(List<MessageSlot> messages) {
        this.messageMap = new LinkedHashMap<>();
        if (messages == null) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            MessageSlot slot = messages.get(i);
            if (slot != null) {
                this.messageMap.put(String.valueOf(i), slot);
            }
        }
    }

    public Map<String, MessageSlot> getMessageMap() {
        return messageMap;
    }

    public void setMessageMap(Map<String, MessageSlot> messageMap) {
        this.messageMap = messageMap == null ? new LinkedHashMap<>() : new LinkedHashMap<>(messageMap);
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
