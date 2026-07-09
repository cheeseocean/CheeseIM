package com.cheeseocean.im.postbox.history;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 附件元数据文档（postbox 只读镜像）：{@code _id = attachmentId}，附件 → 所属消息的反查索引。
 * 写入方为 postmaster {@code BlockHistoryPersistenceService}，
 * postbox 侧仅用于附件鉴权按 attachmentId 点查（ASSESSMENT P1-10）。
 *
 * @author xxxcrel
 */
@Document("attachment_metadata")
public class AttachmentMetadataDoc {

    /** attachmentId */
    @Id
    private String id;
    private String conversationId;
    private String serverMsgId;
    private String clientMsgId;
    private Long seq;
    private String senderId;
    private Integer contentType;
    private Long sendTime;
    private Instant createdAt;

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

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public Integer getContentType() {
        return contentType;
    }

    public void setContentType(Integer contentType) {
        this.contentType = contentType;
    }

    public Long getSendTime() {
        return sendTime;
    }

    public void setSendTime(Long sendTime) {
        this.sendTime = sendTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
