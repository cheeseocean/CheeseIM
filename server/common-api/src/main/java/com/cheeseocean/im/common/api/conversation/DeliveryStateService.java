package com.cheeseocean.im.common.api.conversation;

import com.cheeseocean.im.common.api.dto.conversation.DeliverySeqUpdate;

/** 客户端设备送达高水位服务。 */
public interface DeliveryStateService {
    DeliverySeqUpdate acknowledge(String userId, String deviceId, String conversationId,
                                  long maxDeliveredSeq, String opId);
}
