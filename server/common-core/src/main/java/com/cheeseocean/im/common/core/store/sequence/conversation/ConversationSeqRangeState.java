package com.cheeseocean.im.common.core.store.sequence.conversation;

/**
 * 会话 seq 缓存段分配结果状态。
 *
 * <p>这组状态只描述缓存层结论，不表示 Mongo 是否已经扩段成功。
 */
public enum ConversationSeqRangeState {
    /**
     * 命中缓存段，已成功分配一段或读取当前 maxSeq。
     */
    ALLOCATED,
    /**
     * 当前会话没有缓存段，需要回源 Mongo 初始化。
     */
    MISS,
    /**
     * 当前会话缓存段正被其他分配者扩段，应等待后重试。
     */
    LOCKED,
    /**
     * 当前缓存段已耗尽，当前分配者已获得扩段锁。
     */
    EXHAUSTED
}
