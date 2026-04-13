package com.cheeseocean.im.common.core.store.sequence.conversation;

/**
 * 会话 seq 缓存段存储。
 *
 * <p>这一层只缓存已经由 Mongo 真相源预留出来的号段，不负责生成最终全局 seq。
 *
 * <p>职责边界：
 * <ul>
 *   <li>维护缓存段状态，例如当前游标、段上界和锁 owner</li>
 *   <li>把缓存层结果抽象成统一状态机返回给 allocator</li>
 *   <li>不直接访问 Mongo，也不决定下一段应该申请多大</li>
 * </ul>
 */
public interface ConversationSeqCacheStore {

    /**
     * 从缓存段中申请一段 seq，或返回需要回源 Mongo 的状态。
     *
     * @param conversationId 会话 ID
     * @param size 请求大小，传 0 表示只读取当前缓存层可见的 maxSeq
     * @param nowMillis 当前时间戳
     * @return 缓存层状态机结果；由上层 allocator 决定是否回源 Mongo
     */
    ConversationSeqCacheResult allocate(String conversationId, int size, long nowMillis);

    /**
     * 将新的缓存段写回缓存存储。
     *
     * <p>通常在上层完成 Mongo 扩段后调用，用于初始化新段或重写异常缓存。
     */
    void install(String conversationId, String ownerToken, long currentSeq, long lastSeq, long timestampMillis);

    /**
     * 查询缓存中的当前 maxSeq，不存在时返回 0。
     *
     * <p>该方法仅访问缓存层，不负责回源 Mongo。
     */
    long getCachedMaxSeq(String conversationId);

    /**
     * 删除会话缓存段。
     *
     * <p>用于调试恢复、手动失效或后续的缓存重建。
     */
    void clear(String conversationId);
}
