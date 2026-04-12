package com.cheeseocean.im.common.api.dto.conversation;

import lombok.Data;

import java.io.Serializable;

/**
 * 单个会话的 seq 拉取区间。
 */
@Data
public class SeqRangeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 ID。
     */
    private String conversationId;
    /**
     * 拉取起始位点，包含。
     */
    private long   beginSeq;
    /**
     * 拉取结束位点，包含。
     */
    private long   endSeq;
}
