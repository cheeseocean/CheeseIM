package com.cheeseocean.im.common.api.conversation;

import com.cheeseocean.im.common.api.dto.conversation.TypingSignal;
import com.cheeseocean.im.common.api.enums.TypingActionEnum;

/**
 * 会话输入中控制信号的唯一入口。
 *
 * <p>实现负责成员校验、TTL 约束和在线控制通知；不得写入普通消息 ingress、历史或 seq 链路。
 */
public interface TypingStateService {

    /**
     * 发布输入中状态。
     *
     * @return 非空表示请求已通过领域校验；没有在线目标或实时投递失败不改变该语义
     */
    TypingSignal publish(String senderId, String conversationId, TypingActionEnum action, int ttlSeconds);
}
