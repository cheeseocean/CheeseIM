package com.cheeseocean.im.common.core.history.model;

import lombok.Data;

import java.time.Instant;

/** attachmentId 到所属消息的查询模型；对象存储细节不属于该模型。 */
@Data
public class AttachmentMetadata {
    private String id;
    private String conversationId;
    private String serverMsgId;
    private String clientMsgId;
    private Long seq;
    private String senderId;
    private Integer contentType;
    private Long sendTime;
    private Instant createdAt;
}
