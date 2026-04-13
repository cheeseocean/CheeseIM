package com.cheeseocean.im.common.core.store.sequence.conversation;

import com.cheeseocean.im.common.core.business.repository.ConversationRangeRepository;
import com.cheeseocean.im.common.core.store.sequence.SequenceRange;

import java.util.Objects;

/**
 * 会话消息 seq 分配器。
 *
 * <p>Mongo 是全局真相源，Redis/RocksDB 只缓存已预留号段。
 *
 * <p>这层负责把缓存状态机与 Mongo 扩段动作组合成稳定的外部语义：
 * <ul>
 *   <li>{@link #next(String)} 返回单个下一 seq</li>
 *   <li>{@link #allocate(String, int)} 返回一段连续 seq</li>
 *   <li>{@link #getMaxSeq(String)} 返回当前会话的已知最大 seq</li>
 * </ul>
 *
 * <p>允许空洞，但不允许重复或回退。
 */
public class ConversationSeqAllocator {

    private final ConversationSeqCacheStore cacheStore;
    private final ConversationRangeRepository conversationRangeRepository;
    private final int singleReserveSize;
    private final int groupReserveSize;
    private final int maxRetries;

    public ConversationSeqAllocator(ConversationSeqCacheStore cacheStore,
                                    ConversationRangeRepository conversationRangeRepository,
                                    int singleReserveSize,
                                    int groupReserveSize,
                                    int maxRetries) {
        this.cacheStore = Objects.requireNonNull(cacheStore, "cacheStore");
        this.conversationRangeRepository = Objects.requireNonNull(conversationRangeRepository, "conversationRangeRepository");
        if (singleReserveSize <= 0 || groupReserveSize <= 0 || maxRetries <= 0) {
            throw new IllegalArgumentException("reserveSize and maxRetries must be positive");
        }
        this.singleReserveSize = singleReserveSize;
        this.groupReserveSize = groupReserveSize;
        this.maxRetries = maxRetries;
    }

    public long next(String conversationId) {
        return allocate(conversationId, 1).startInclusive();
    }

    /**
     * 为会话申请一段连续 seq。
     *
     * <p>对调用方隐藏缓存 miss、锁等待、Mongo 扩段等细节。
     */
    public SequenceRange allocate(String conversationId, int size) {
        return allocate(conversationId, size, System.currentTimeMillis());
    }

    SequenceRange allocate(String conversationId, int size, long nowMillis) {
        Objects.requireNonNull(conversationId, "conversationId");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        // 缓存层可能因为并发扩段而短暂返回 LOCKED，因此这里采用有限重试。
        for (int i = 0; i < maxRetries; i++) {
            ConversationSeqCacheResult result = cacheStore.allocate(conversationId, size, nowMillis);
            switch (result.state()) {
                case ALLOCATED:
                    // 命中缓存段时，currentSeq 是分配前的最大值。
                    return new SequenceRange(result.currentSeq() + 1L, result.currentSeq() + size);
                case MISS:
                    // 首次分配或缓存丢失：从 Mongo 初始化一整段缓存。
                    return initializeFromMongo(conversationId, size, nowMillis, result);
                case EXHAUSTED:
                    // 当前调用方获得扩段资格，继续向 Mongo 预留下一段。
                    return expandFromMongo(conversationId, size, result);
                case LOCKED:
                    // 其他调用方正在扩段，短暂等待后重试。
                    waitBriefly();
                    break;
                default:
                    throw new IllegalStateException("Unexpected cache state: " + result.state());
            }
        }
        throw new IllegalStateException("Timed out waiting for conversation seq cache lock");
    }

    public long getMaxSeq(String conversationId) {
        return getMaxSeq(conversationId, System.currentTimeMillis());
    }

    /**
     * 获取会话当前 maxSeq。
     *
     * <p>优先读取缓存层；缓存 miss 时回源 Mongo，并把结果重新安装回缓存。
     */
    long getMaxSeq(String conversationId, long nowMillis) {
        Objects.requireNonNull(conversationId, "conversationId");
        for (int i = 0; i < maxRetries; i++) {
            ConversationSeqCacheResult result = cacheStore.allocate(conversationId, 0, nowMillis);
            switch (result.state()) {
                case ALLOCATED, EXHAUSTED:
                    // size=0 时 currentSeq 表示缓存层当前可见的 maxSeq。
                    return result.currentSeq();
                case MISS:
                    // 读路径遇到缓存 miss，不扩段，只同步 Mongo 当前值。
                    long maxSeq = conversationRangeRepository.getMaxSeq(conversationId);
                    cacheStore.install(conversationId, result.ownerToken(), maxSeq, maxSeq, nowMillis);
                    return maxSeq;
                case LOCKED:
                    waitBriefly();
                    break;
                default:
                    throw new IllegalStateException("Unexpected cache state: " + result.state());
            }
        }
        throw new IllegalStateException("Timed out waiting for conversation max seq");
    }

    private SequenceRange initializeFromMongo(String conversationId,
                                              int size,
                                              long nowMillis,
                                              ConversationSeqCacheResult result) {
        // ConversationRangeRepository.allocate 返回的是分配前 maxSeq。
        long reserveSize = getReserveSize(conversationId, size);
        long previousMaxSeq = conversationRangeRepository.allocate(conversationId, reserveSize);
        long startInclusive = previousMaxSeq + 1L;
        long endInclusive = previousMaxSeq + size;
        // currentSeq 安装为“本次已经实际消费完成的末尾”，
        // lastSeq 安装为“整个缓存段的上界”。
        cacheStore.install(
                conversationId,
                result.ownerToken(),
                endInclusive,
                previousMaxSeq + reserveSize,
                nowMillis
        );
        return new SequenceRange(startInclusive, endInclusive);
    }

    private SequenceRange expandFromMongo(String conversationId, int size, ConversationSeqCacheResult result) {
        long reserveSize = getReserveSize(conversationId, size);
        long previousMaxSeq = conversationRangeRepository.allocate(conversationId, reserveSize);
        if (previousMaxSeq == result.lastSeq()) {
            // 正常路径：Mongo 的旧 maxSeq 与缓存 LAST 对齐，可从缓存 currentSeq 后续继续分配。
            long startInclusive = result.currentSeq() + 1L;
            long endInclusive = result.currentSeq() + size;
            cacheStore.install(
                    conversationId,
                    result.ownerToken(),
                    endInclusive,
                    previousMaxSeq + reserveSize,
                    result.timestampMillis()
            );
            return new SequenceRange(startInclusive, endInclusive);
        }

        // 修正路径：缓存 LAST 与 Mongo 不一致，以 Mongo 真相源为准重写缓存。
        // 这样会产生空洞，但不会产生重复。
        long startInclusive = previousMaxSeq + 1L;
        long endInclusive = previousMaxSeq + size;
        cacheStore.install(
                conversationId,
                result.ownerToken(),
                endInclusive,
                previousMaxSeq + reserveSize,
                result.timestampMillis()
        );
        return new SequenceRange(startInclusive, endInclusive);
    }

    private long getReserveSize(String conversationId, int requestedSize) {
        // 群聊 fanout 更容易形成热点，因此默认缓存段比单聊更大。
        long base = conversationId.startsWith("g:")
                ? groupReserveSize
                : singleReserveSize;
        return base + requestedSize;
    }

    private void waitBriefly() {
        try {
            // 这里使用固定短等待，先保证实现简单和可预测；
            // 后续若需要可演进为带抖动的退避。
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for seq cache lock", e);
        }
    }
}
