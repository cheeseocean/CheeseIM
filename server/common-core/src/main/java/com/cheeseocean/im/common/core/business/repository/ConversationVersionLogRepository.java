package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.ConversationVersionLog;
import com.cheeseocean.im.common.api.enums.ConversationVersionOperation;

import java.util.List;
import java.util.Optional;

/**
 * 用户会话元数据版本日志仓储。
 */
public interface ConversationVersionLogRepository {

    /**
     * 追加一条会话变更日志，并返回分配后的版本信息。
     */
    ConversationVersionLog append(String ownerUserId, String conversationId, ConversationVersionOperation operation);

    /**
     * 查询用户当前最新会话版本。
     */
    Optional<ConversationVersionLog> findLatest(String ownerUserId);

    /**
     * 查询指定版本之后的会话变更日志。
     */
    List<ConversationVersionLog> findAfter(String ownerUserId, String versionId, long version, int limit);
}
