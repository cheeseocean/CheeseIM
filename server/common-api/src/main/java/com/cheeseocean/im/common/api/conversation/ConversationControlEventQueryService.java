package com.cheeseocean.im.common.api.conversation;

import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;

import java.util.List;

/**
 * 会话控制事件同步查询契约。
 *
 * <p>HTTP 层只通过该契约读取事件，控制事件的持久化细节保留在 postmaster。</p>
 */
public interface ConversationControlEventQueryService {

    List<ConversationControlEvent> findAfter(String userId, long cursor, int limit);
}
