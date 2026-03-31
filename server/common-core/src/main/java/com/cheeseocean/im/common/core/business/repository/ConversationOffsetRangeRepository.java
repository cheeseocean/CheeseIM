package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.core.business.domain.ConversationOffsetRange;

import java.util.List;
import java.util.Optional;

/**
 * 用户-会话同步偏移量仓储抽象接口。
 *
 * <p>管理 {@link ConversationOffsetRange} 的持久化，
 * 存储每位用户在每个会话中的三个序列水位（maxSeq / minSeq / readSeq）。
 *
 * <p>该仓储是已读回执（read receipt）和多端同步的核心写路径，
 * 故意与业务属性表分离，
 * 以减小文档体积、降低高频写入带来的并发竞争。
 */
public interface ConversationOffsetRangeRepository {

    /**
     * 若记录不存在则插入，已存在则忽略（幂等）。
     * 通常在会话首次激活时调用，为后续 maxSeq / readSeq 更新做准备。
     */
    void createIfAbsent(String ownerUserId, String conversationId);

    /**
     * 更新用户已读水位线（readSeq）。
     * 由 ReadSeqPersistenceWriter 异步批量调用。
     */
    void updateReadSeq(String ownerUserId, String conversationId, long readSeq);

    /**
     * 更新服务端最大序列号（maxSeq）。
     * 由消息投递链路在新消息分配 seq 后调用，确保客户端知道最新上界。
     */
    void updateMaxSeq(String ownerUserId, String conversationId, long maxSeq);

    /**
     * 更新历史消息可见下界（minSeq）。
     * 由消息清理任务调用，推进不可见水位线。
     */
    void updateMinSeq(String ownerUserId, String conversationId, long minSeq);

    /**
     * 查询单条偏移量记录，不存在时返回 empty。
     */
    Optional<ConversationOffsetRange> find(String ownerUserId, String conversationId);

    /**
     * 批量查询用户在指定会话中的偏移量记录。
     * 不存在的会话直接跳过。
     */
    List<ConversationOffsetRange> findByIds(String ownerUserId, List<String> conversationIds);

    /**
     * 查询用户在所有会话中的偏移量列表。
     * 客户端全量同步时使用。
     */
    List<ConversationOffsetRange> findByOwner(String ownerUserId);
}
