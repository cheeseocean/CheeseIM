package com.cheeseocean.im.common.core.business.repository;

/** 设备会话送达高水位持久化 seam。 */
public interface DeviceConversationDeliveryRepository {
    void updateDeliveredSeq(String userId, String deviceId, String conversationId, long deliveredSeq);
}
