package com.cheeseocean.im.apiserver.model.request;

import lombok.Data;

/**
 * HTTP 单会话 seq 区间请求项。
 */
@Data
public class SeqRangeItemRequest {

    private String conversationId;
    private long   beginSeq;
    private long   endSeq;
}
