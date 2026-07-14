package com.cheeseocean.im.common.api.dto.message;

import lombok.Data;

import java.io.Serializable;

/**
 * 单条消息撤回结果。
 */
@Data
public class MessageMutationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String errorCode;
    private String errorMessage;
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

    /** 创建失败结果，避免将技术异常暴露到协议层。 */
    public static MessageMutationResult rejected(String code, String message) {
        MessageMutationResult result = new MessageMutationResult();
        result.setSuccess(false);
        result.setErrorCode(code);
        result.setErrorMessage(message);
        return result;
    }
}
