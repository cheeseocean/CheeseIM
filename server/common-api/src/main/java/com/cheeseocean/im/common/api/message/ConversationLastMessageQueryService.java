package com.cheeseocean.im.common.api.message;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;

import java.util.List;
import java.util.Map;

/**
 * 按会话批量查询最新消息摘要。
 */
public interface ConversationLastMessageQueryService {

    /**
     * 返回每个会话当前可展示的最新消息摘要。
     * 缺失会话不会出现在返回结果中。
     */
    Map<String, ConversationLastMessageSummary> getLatestMessages(List<String> conversationIds);
}
