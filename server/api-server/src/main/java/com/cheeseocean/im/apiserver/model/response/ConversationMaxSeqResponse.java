package com.cheeseocean.im.apiserver.model.response;

import lombok.Data;

/**
 * 会话最大序列号响应。
 */
@Data
public class ConversationMaxSeqResponse {

    private String conversationId;
    private long   maxSeq;
}
