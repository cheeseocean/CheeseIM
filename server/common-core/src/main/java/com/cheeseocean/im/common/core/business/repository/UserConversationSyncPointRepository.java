package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.UserConversationSyncPoint;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户-会话同步位点仓储抽象接口。
 *
 * <p>管理 {@link UserConversationSyncPoint} 的持久化，
 * 存储每位用户在每个会话中的三个序列水位（maxSeq / minSeq / readSeq）。
 *
 * <p>该仓储是已读回执（read receipt）和多端同步的核心写路径，
 * 故意与业务属性表分离，
 * 以减小文档体积、降低高频写入带来的并发竞争。
 */
public interface UserConversationSyncPointRepository {

    /**
     * 更新用户已读水位线（readSeq）。
     * 由 ReadSeqPersistenceWriter 异步批量调用。
     */
    void updateReadSeq(String userId, String conversationId, long readSeq);

    /** 查询用户在指定会话中的已同步最大序列号。 */
    long getMaxSeq(String userId, String conversationId);

    /**
     * 更新服务端最大序列号（maxSeq）。
     * 由消息投递链路在新消息分配 seq 后调用，确保客户端知道最新上界。
     */
    void updateMaxSeq(String userId, String conversationId, long maxSeq);

    /**
     * 批量单调推进服务端最大序列号。
     *
     * <p>实现必须使用 max 语义承受多副本乱序；默认实现仅用于非 Mongo 适配器兼容。</p>
     */
    default void updateMaxSeqBatch(List<MaxSeqUpdate> updates) {
        if (updates == null) {
            return;
        }
        for (MaxSeqUpdate update : updates) {
            if (update != null) {
                updateMaxSeq(update.userId(), update.conversationId(), update.maxSeq());
            }
        }
    }

    record MaxSeqUpdate(String userId, String conversationId, long maxSeq) {
    }

    /** 查询用户在指定会话中的最小可见序列号。 */
    long getMinSeq(String userId, String conversationId);

    /**
     * 更新历史消息可见下界（minSeq）。
     * 由消息清理任务调用，推进不可见水位线。
     */
    void updateMinSeq(String userId, String conversationId, long minSeq);

    /** 查询用户在指定会话中的已读序列号。 */
    long getReadSeq(String userId, String conversationId);

    /**
     * 查询用户在指定会话集合中的已读序列号映射。
     * 缺失的会话以 0L 填充。
     */
    Map<String, Long> getReadSeqMap(String userId, List<String> conversationIds);

    /**
     * 查询单条偏移量记录，不存在时返回 empty。
     */
    Optional<UserConversationSyncPoint> find(String userId, String conversationId);

    /**
     * 批量查询用户在指定会话中的偏移量记录。
     * 不存在的会话直接跳过。
     */
    List<UserConversationSyncPoint> findByIds(String userId, List<String> conversationIds);

    /**
     * 查询用户在所有会话中的偏移量列表。
     * 客户端全量同步时使用。
     */
    List<UserConversationSyncPoint> findByUserId(String userId);
}
