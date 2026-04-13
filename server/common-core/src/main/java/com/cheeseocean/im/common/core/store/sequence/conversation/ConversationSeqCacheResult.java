package com.cheeseocean.im.common.core.store.sequence.conversation;

/**
 * 会话 seq 缓存段操作结果。
 *
 * <p>{@code currentSeq} 表示当前已分配到的最大 seq，分配成功时本次实际分配区间为
 * {@code [currentSeq + 1, currentSeq + size]}。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code state}: 当前缓存层状态</li>
 *   <li>{@code currentSeq}: 本次操作前缓存层看到的当前最大 seq</li>
 *   <li>{@code lastSeq}: 当前缓存段上界</li>
 *   <li>{@code ownerToken}: 扩段锁拥有者标识，仅在需要 install 时有意义</li>
 *   <li>{@code timestampMillis}: 缓存层时间字段</li>
 * </ul>
 */
public record ConversationSeqCacheResult(
        ConversationSeqRangeState state,
        long currentSeq,
        long lastSeq,
        String ownerToken,
        long timestampMillis
) {
}
