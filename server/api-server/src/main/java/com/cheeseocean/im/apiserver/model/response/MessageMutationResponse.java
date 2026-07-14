package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

/** 消息 mutation 的 HTTP 表示，避免向 Controller 泄露领域对象。 */
@Data
public class MessageMutationResponse {

    private String mutationId;
    private String conversationId;
    private String serverMsgId;
    private String operatorUserId;
    private String operatorName;
    private String targetSenderId;
    private String targetSenderName;
    private long revokedAt;
    private long mutationVersion;
    private String reason;
}
