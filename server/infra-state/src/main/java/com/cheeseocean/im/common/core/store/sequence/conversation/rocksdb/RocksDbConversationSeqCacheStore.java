package com.cheeseocean.im.common.core.store.sequence.conversation.rocksdb;

import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqCacheResult;
import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqCacheStore;
import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqRangeState;
import com.cheeseocean.im.common.core.util.ObjectMapperFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * 单机模式下的 RocksDB 会话 seq 缓存段实现。
 *
 * <p>它只承担“无 Redis 时的本机缓存段”角色，不承担多节点共享一致性。
 */
public class RocksDbConversationSeqCacheStore implements ConversationSeqCacheStore {

    private final RocksDbSupport support;
    private final long lockTtlMillis;

    public RocksDbConversationSeqCacheStore(Path dataDirectory) {
        this(dataDirectory, 3_000L);
    }

    public RocksDbConversationSeqCacheStore(Path dataDirectory, long lockTtlMillis) {
        this.support = new RocksDbSupport(
                Objects.requireNonNull(dataDirectory, "dataDirectory"),
                ObjectMapperFactory.createDefaultMapper()
        );
        this.lockTtlMillis = lockTtlMillis;
    }

    @Override
    public synchronized ConversationSeqCacheResult allocate(String conversationId, int size, long nowMillis) {
        Objects.requireNonNull(conversationId, "conversationId");
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        SeqCacheState state = load(conversationId);
        if (state == null || state.isLockExpired(nowMillis)) {
            // 没有缓存段或旧锁过期时，当前调用方接管初始化流程。
            String owner = UUID.randomUUID().toString();
            save(conversationId, new SeqCacheState(0L, 0L, nowMillis, owner, nowMillis + lockTtlMillis));
            return new ConversationSeqCacheResult(ConversationSeqRangeState.MISS, 0L, 0L, owner, nowMillis);
        }
        if (state.lockOwner() != null) {
            return new ConversationSeqCacheResult(
                    ConversationSeqRangeState.LOCKED,
                    state.currentSeq(),
                    state.lastSeq(),
                    state.lockOwner(),
                    state.timestampMillis()
            );
        }
        if (size == 0) {
            return new ConversationSeqCacheResult(
                    ConversationSeqRangeState.ALLOCATED,
                    state.currentSeq(),
                    state.lastSeq(),
                    null,
                    state.timestampMillis()
            );
        }
        long nextCurrentSeq = state.currentSeq() + size;
        if (nextCurrentSeq > state.lastSeq()) {
            // 缓存段耗尽后，先把当前值收敛到 last，再由持锁方回源 Mongo 扩段。
            String owner = UUID.randomUUID().toString();
            SeqCacheState exhausted = new SeqCacheState(
                    state.lastSeq(),
                    state.lastSeq(),
                    nowMillis,
                    owner,
                    nowMillis + lockTtlMillis
            );
            save(conversationId, exhausted);
            return new ConversationSeqCacheResult(
                    ConversationSeqRangeState.EXHAUSTED,
                    state.currentSeq(),
                    state.lastSeq(),
                    owner,
                    nowMillis
            );
        }
        SeqCacheState updated = new SeqCacheState(nextCurrentSeq, state.lastSeq(), nowMillis, null, 0L);
        save(conversationId, updated);
        return new ConversationSeqCacheResult(
                ConversationSeqRangeState.ALLOCATED,
                state.currentSeq(),
                state.lastSeq(),
                null,
                nowMillis
        );
    }

    @Override
    public synchronized void install(String conversationId,
                                     String ownerToken,
                                     long currentSeq,
                                     long lastSeq,
                                     long timestampMillis) {
        SeqCacheState state = load(conversationId);
        if (state != null && state.lockOwner() != null && !state.lockOwner().equals(ownerToken)) {
            // 其他调用方已经取得扩段权，当前结果直接丢弃，避免覆盖更新段。
            return;
        }
        save(conversationId, new SeqCacheState(currentSeq, lastSeq, timestampMillis, null, 0L));
    }

    @Override
    public synchronized long getCachedMaxSeq(String conversationId) {
        SeqCacheState state = load(conversationId);
        return state == null ? 0L : state.currentSeq();
    }

    @Override
    public synchronized void clear(String conversationId) {
        support.delete(key(conversationId));
    }

    private SeqCacheState load(String conversationId) {
        return support.get(key(conversationId), SeqCacheState.class);
    }

    private void save(String conversationId, SeqCacheState state) {
        support.put(key(conversationId), state, null);
    }

    private String key(String conversationId) {
        return "conversation-seq:" + conversationId;
    }

    public record SeqCacheState(long currentSeq,
                                long lastSeq,
                                long timestampMillis,
                                String lockOwner,
                                long lockExpiresAtMillis) {
        /**
         * 仅识别锁是否已经失效。
         * RocksDB 版不对数据本身做短 TTL 管理。
         */
        public boolean isLockExpired(long nowMillis) {
            return lockOwner != null && lockExpiresAtMillis > 0L && lockExpiresAtMillis <= nowMillis;
        }
    }
}
