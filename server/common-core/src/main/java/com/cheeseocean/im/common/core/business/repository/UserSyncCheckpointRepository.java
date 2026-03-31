package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.core.business.domain.UserSyncCheckpoint;

import java.util.List;
import java.util.Optional;

/**
 * 用户-会话同步位点仓储抽象接口。
 *
 * <p>管理 {@link UserSyncCheckpoint} 的持久化，独立于
 * {@link ConversationOffsetRangeRepository} 存储。
 */
public interface UserSyncCheckpointRepository {

    void createIfAbsent(String userId, String conversationId);

    void updateReadSeq(String userId, String conversationId, long readSeq);

    void updateMaxSeq(String userId, String conversationId, long maxSeq);

    void updateMinSeq(String userId, String conversationId, long minSeq);

    Optional<UserSyncCheckpoint> find(String userId, String conversationId);

    List<UserSyncCheckpoint> findByIds(String userId, List<String> conversationIds);

    List<UserSyncCheckpoint> findByUserId(String userId);
}
