package com.cheeseocean.im.common.core.history.document;

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

/** Mongo 历史消息块，持久化细节只归 common-core 管理。 */
@Document("message_block")
@CompoundIndexes({@CompoundIndex(name = "idx_message_block_conversation_block", def = "{'conversationId': 1, 'blockNo': -1}")})
public class MessageBlockDoc {
    @Id private String id;
    private String conversationId;
    private Long blockNo;
    private Long startSeq;
    private Long endSeq;
    @Field("messages") private Map<String, MessageSlot> messageMap = new LinkedHashMap<>();
    private Instant createdAt;
    private Instant updatedAt;
    public String getId() { return id; } public void setId(String value) { id = value; }
    public String getConversationId() { return conversationId; } public void setConversationId(String value) { conversationId = value; }
    public Long getBlockNo() { return blockNo; } public void setBlockNo(Long value) { blockNo = value; }
    public Long getStartSeq() { return startSeq; } public void setStartSeq(Long value) { startSeq = value; }
    public Long getEndSeq() { return endSeq; } public void setEndSeq(Long value) { endSeq = value; }
    public List<MessageSlot> getMessages() {
        if (messageMap == null || messageMap.isEmpty()) return new ArrayList<>();
        int max = messageMap.keySet().stream().filter(key -> key.matches("\\d+")).mapToInt(Integer::parseInt).max().orElse(-1);
        List<MessageSlot> result = new ArrayList<>(java.util.Collections.nCopies(max + 1, null));
        messageMap.forEach((key, value) -> { if (key.matches("\\d+")) result.set(Integer.parseInt(key), value); });
        return result;
    }
    public void setMessages(List<MessageSlot> values) { messageMap = new LinkedHashMap<>(); if (values != null) for (int i = 0; i < values.size(); i++) if (values.get(i) != null) messageMap.put(String.valueOf(i), values.get(i)); }
    public Map<String, MessageSlot> getMessageMap() { return messageMap; } public void setMessageMap(Map<String, MessageSlot> value) { messageMap = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value); }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}
