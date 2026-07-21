package com.cheeseocean.im.common.core.history.model;

import lombok.Data;

import java.time.Instant;

/** 消息撤回/编辑 overlay 的领域模型，不允许业务层依赖 Mongo Document。 */
@Data
public class MessageMutation {
    private String id;
    private String serverMsgId;
    private String conversationId;
    private Integer mutationType;
    private String operatorUserId;
    private String operatorName;
    private String targetSenderId;
    private String targetSenderName;
    private String reason;
    private Long mutationVersion;
    private Instant createdAt;
}
