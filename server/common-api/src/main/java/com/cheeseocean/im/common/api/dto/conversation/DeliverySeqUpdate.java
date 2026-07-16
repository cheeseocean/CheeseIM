package com.cheeseocean.im.common.api.dto.conversation;

import lombok.Data;

import java.io.Serializable;

/** 设备会话送达高水位的实际推进结果。 */
@Data
public class DeliverySeqUpdate implements Serializable {
    private String conversationId;
    private String recipientUserId;
    private String deviceId;
    private long deliveredSeq;
    private boolean changed;
}
