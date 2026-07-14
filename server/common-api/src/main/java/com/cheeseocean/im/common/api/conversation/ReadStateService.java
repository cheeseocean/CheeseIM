package com.cheeseocean.im.common.api.conversation;

import com.cheeseocean.im.common.api.dto.conversation.ReadSeqUpdate;

/**
 * 会话已读状态服务。
 *
 * <p>所有客户端入口均通过此接口推进 readSeq，保证成员校验、上界截断、单调写入和
 * 会话版本日志只实现一次。
 */
public interface ReadStateService {

    /**
     * 确认用户在会话中的已读高水位。
     *
     * @return 无效用户、会话或非正位点时返回 {@code null}；其余情况返回实际状态结果
     */
    ReadSeqUpdate acknowledge(String userId, String conversationId, long readSeq);
}
