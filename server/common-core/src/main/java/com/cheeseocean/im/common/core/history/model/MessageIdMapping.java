package com.cheeseocean.im.common.core.history.model;

import lombok.Data;

import java.time.Instant;

/** client/server message id 的历史查询模型，不暴露 Mongo 索引注解。 */
@Data
public class MessageIdMapping {
    private String id;
    private String conversationId;
    private String clientMsgId;
    private String serverMsgId;
    private Long seq;
    private String senderId;
    private Long sendTime;
    private Instant createdAt;
}
