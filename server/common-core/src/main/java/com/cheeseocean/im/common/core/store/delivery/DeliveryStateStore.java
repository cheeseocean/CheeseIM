package com.cheeseocean.im.common.core.store.delivery;

/** 用户设备在会话中的送达高水位热状态。 */
public interface DeliveryStateStore {
    record AdvanceResult(long deliveredSeq, boolean changed) {}

    AdvanceResult advance(String userId, String deviceId, String conversationId, long requestedSeq);
}
