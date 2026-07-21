package com.cheeseocean.im.storage.history.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** client/server message id Mongo 反查索引。 */
@Document("message_id_mapping")
public class MessageIdMappingDoc {
    @Id private String id;
    private String conversationId;
    private String clientMsgId;
    @Indexed private String serverMsgId;
    private Long seq;
    private String senderId;
    private Long sendTime;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String value) { conversationId = value; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String value) { clientMsgId = value; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String value) { serverMsgId = value; }
    public Long getSeq() { return seq; }
    public void setSeq(Long value) { seq = value; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String value) { senderId = value; }
    public Long getSendTime() { return sendTime; }
    public void setSendTime(Long value) { sendTime = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
