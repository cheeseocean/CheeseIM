package com.cheeseocean.im.apiserver.model.request;

import lombok.Data;

/**
 * HTTP 已读位点确认请求。
 */
@Data
public class AckReadSeqRequest {

    /**
     * 新的已读位点。
     */
    private long readSeq;
}
