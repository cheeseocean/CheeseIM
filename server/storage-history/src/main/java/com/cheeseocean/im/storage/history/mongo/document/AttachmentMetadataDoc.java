package com.cheeseocean.im.storage.history.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** attachmentId 到消息的 Mongo 点查索引。 */
@Document("attachment_metadata")
public class AttachmentMetadataDoc {
    @Id private String id;
    private String conversationId;
    private String serverMsgId;
    private String clientMsgId;
    private Long seq;
    private String senderId;
    private Integer contentType;
    private Long sendTime;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String value) { conversationId = value; }
    public String getServerMsgId() { return serverMsgId; }
    public void setServerMsgId(String value) { serverMsgId = value; }
    public String getClientMsgId() { return clientMsgId; }
    public void setClientMsgId(String value) { clientMsgId = value; }
    public Long getSeq() { return seq; }
    public void setSeq(Long value) { seq = value; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String value) { senderId = value; }
    public Integer getContentType() { return contentType; }
    public void setContentType(Integer value) { contentType = value; }
    public Long getSendTime() { return sendTime; }
    public void setSendTime(Long value) { sendTime = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
