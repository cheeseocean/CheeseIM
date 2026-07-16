package com.cheeseocean.im.common.core.store.typing;

import com.cheeseocean.im.common.api.enums.TypingActionEnum;

/**
 * 输入中瞬时状态存储。
 *
 * <p>状态只用于跨实例节流和短时过期，不属于消息或控制事件的持久化真相。</p>
 */
public interface TypingStateStore {

    /**
     * 原子更新输入状态。
     *
     * @return START 首次占位或 STOP 成功清除已有状态时返回 true；重复信号返回 false
     */
    boolean update(String senderId, String conversationId, TypingActionEnum action, int ttlSeconds);
}
