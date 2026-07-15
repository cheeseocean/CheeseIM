package com.cheeseocean.im.common.api.message;

import com.cheeseocean.im.common.api.dto.message.HistoryMessage;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.session.SessionPrincipal;

import java.util.List;

/**
 * 消息历史查询契约。
 *
 * <p>历史真相由 postmaster 持久化，postbox 负责提供此只读查询能力。
 */
public interface MessageHistoryQueryService {

    /**
     * 查询指定会话的最近一页消息，并校验当前会话主体的读取权限。
     */
    List<HistoryMessage> getConversationMessages(SessionPrincipal session, String conversationId, int limit);

    /**
     * 按会话序列区间拉取消息，用于断线同步和 gap repair。
     */
    List<Message> pullMessagesBySeqRange(String conversationId, long beginSeq, long endSeq, int limit);
}
