package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.ConversationRange;

/**
 * 会话级序列范围仓储抽象接口。
 *
 * <p>用于维护会话全局消息序列分配与可见下界。
 */
public interface ConversationRangeRepository {

    /**
     * 为会话分配一段连续 seq，返回分配前的起始 seq。
     */
    long allocate(String conversationId, long size);

    /** 更新会话当前最大 seq。 */
    void setMaxSeq(String conversationId, long seq);

    /** 查询会话当前最大 seq。 */
    long getMaxSeq(String conversationId);

    /** 更新会话当前最小 seq。 */
    void setMinSeq(String conversationId, long seq);

    /** 查询会话当前最小 seq。 */
    long getMinSeq(String conversationId);

    /** 查询会话范围记录，不存在时返回 null。 */
    ConversationRange find(String conversationId);
}
