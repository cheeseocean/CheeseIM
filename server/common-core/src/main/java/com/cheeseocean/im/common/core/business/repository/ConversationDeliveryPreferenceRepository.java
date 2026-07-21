package com.cheeseocean.im.common.core.business.repository;

import java.util.List;

/**
 * 会话维度投递偏好读模型仓储。
 */
public interface ConversationDeliveryPreferenceRepository {

    /**
     * 写入非默认偏好；默认 RECEIVE 会删除冗余读模型记录。
     */
    void setReceiveOptions(List<String> ownerUserIds,
                           String conversationId,
                           int receiveOption);

    /**
     * 删除指定用户会话的偏好记录。
     */
    void remove(String ownerUserId, String conversationId);

    /**
     * 查询屏蔽该会话的用户 ID。
     */
    List<String> findBlockedOwnerUserIds(String conversationId);
}
