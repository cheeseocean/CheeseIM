package com.cheeseocean.im.common.core.store.sequence.conversation.redis;

import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqCacheResult;
import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqCacheStore;
import com.cheeseocean.im.common.core.store.sequence.conversation.ConversationSeqRangeState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Redis 会话 seq 缓存段实现。
 *
 * <p>当前版本采用单 key hash 保存：
 * <ul>
 *   <li>{@code CURR}: 当前已消费到的最大 seq</li>
 *   <li>{@code LAST}: 当前缓存段上界</li>
 *   <li>{@code TIME}: 最近分配时间</li>
 *   <li>{@code LOCK}: 扩段锁 owner</li>
 * </ul>
 *
 * <p>后续可以按设计文档演进为 data key + lock key 双 key 模式，
 * 但不会影响当前接口层。
 */
public class RedisConversationSeqCacheStore implements ConversationSeqCacheStore {

    private final StringRedisTemplate redisTemplate;
    private final long lockTtlSeconds;
    private final long dataTtlSeconds;
    private final DefaultRedisScript<List> allocateScript;
    private final DefaultRedisScript<Long> installScript;

    public RedisConversationSeqCacheStore(StringRedisTemplate redisTemplate, long lockTtlSeconds, long dataTtlSeconds) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.lockTtlSeconds = lockTtlSeconds;
        this.dataTtlSeconds = dataTtlSeconds;
        this.allocateScript = new DefaultRedisScript<>(allocateLua(), List.class);
        this.installScript = new DefaultRedisScript<>(installLua(), Long.class);
    }

    @Override
    public ConversationSeqCacheResult allocate(String conversationId, int size, long nowMillis) {
        Objects.requireNonNull(conversationId, "conversationId");
        // owner 在 MISS / EXHAUSTED 时用于 install 阶段校验扩段拥有者。
        String owner = UUID.randomUUID().toString();
        List<?> result = redisTemplate.execute(
                allocateScript,
                Collections.singletonList(key(conversationId)),
                String.valueOf(size),
                owner,
                String.valueOf(lockTtlSeconds),
                String.valueOf(dataTtlSeconds),
                String.valueOf(nowMillis)
        );
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("Redis conversation seq allocate returned empty result");
        }
        long state = parseLong(result.get(0));
        return switch ((int) state) {
            case 0 -> new ConversationSeqCacheResult(
                    ConversationSeqRangeState.ALLOCATED,
                    parseLong(result.get(1)),
                    parseLong(result.get(2)),
                    null,
                    parseLong(result.get(3))
            );
            case 1 -> new ConversationSeqCacheResult(
                    ConversationSeqRangeState.MISS,
                    0L,
                    0L,
                    String.valueOf(result.get(1)),
                    parseLong(result.get(2))
            );
            case 2 -> new ConversationSeqCacheResult(ConversationSeqRangeState.LOCKED, 0L, 0L, null, nowMillis);
            case 3 -> new ConversationSeqCacheResult(
                    ConversationSeqRangeState.EXHAUSTED,
                    parseLong(result.get(1)),
                    parseLong(result.get(2)),
                    String.valueOf(result.get(3)),
                    parseLong(result.get(4))
            );
            default -> throw new IllegalStateException("Unknown Redis conversation seq state: " + state);
        };
    }

    @Override
    public void install(String conversationId,
                        String ownerToken,
                        long currentSeq,
                        long lastSeq,
                        long timestampMillis) {
        // install 负责把 Mongo 已预留出的新号段写回 Redis，并释放锁。
        Long result = redisTemplate.execute(
                installScript,
                Collections.singletonList(key(conversationId)),
                ownerToken,
                String.valueOf(dataTtlSeconds),
                String.valueOf(currentSeq),
                String.valueOf(lastSeq),
                String.valueOf(timestampMillis)
        );
        if (result == null) {
            throw new IllegalStateException("Redis conversation seq install returned null");
        }
    }

    @Override
    public long getCachedMaxSeq(String conversationId) {
        Object value = redisTemplate.opsForHash().get(key(conversationId), "CURR");
        return value == null ? 0L : parseLong(value);
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(key(conversationId));
    }

    private String key(String conversationId) {
        return "im:seq:conv:" + conversationId;
    }

    private long parseLong(Object value) {
        return Long.parseLong(String.valueOf(value));
    }

    private String allocateLua() {
        return """
                local key = KEYS[1]
                local size = tonumber(ARGV[1])
                local owner = ARGV[2]
                local lockSeconds = tonumber(ARGV[3])
                local dataSeconds = tonumber(ARGV[4])
                local nowMillis = tonumber(ARGV[5])
                if redis.call('EXISTS', key) == 0 then
                    -- 首次 miss：当前调用方拿锁，后续回源 Mongo 初始化缓存段
                    redis.call('HSET', key, 'LOCK', owner)
                    redis.call('EXPIRE', key, lockSeconds)
                    return {1, owner, nowMillis}
                end
                if redis.call('HEXISTS', key, 'LOCK') == 1 then
                    -- 已被其他节点锁定，当前调用方等待重试
                    return {2}
                end
                local currSeq = tonumber(redis.call('HGET', key, 'CURR'))
                local lastSeq = tonumber(redis.call('HGET', key, 'LAST'))
                if size == 0 then
                    -- 读取当前 maxSeq，不触发扩段
                    redis.call('EXPIRE', key, dataSeconds)
                    local setTime = redis.call('HGET', key, 'TIME')
                    if not setTime then setTime = 0 end
                    return {0, currSeq, lastSeq, setTime}
                end
                local nextSeq = currSeq + size
                if nextSeq > lastSeq then
                    -- 当前缓存段已耗尽，当前调用方持锁回源 Mongo 扩段
                    redis.call('HSET', key, 'LOCK', owner)
                    redis.call('HSET', key, 'CURR', lastSeq)
                    redis.call('HSET', key, 'TIME', nowMillis)
                    redis.call('EXPIRE', key, lockSeconds)
                    return {3, currSeq, lastSeq, owner, nowMillis}
                end
                redis.call('HSET', key, 'CURR', nextSeq)
                redis.call('HSET', key, 'TIME', nowMillis)
                redis.call('EXPIRE', key, dataSeconds)
                -- 正常命中缓存段时，返回分配前 currentSeq
                return {0, currSeq, lastSeq, nowMillis}
                """;
    }

    private String installLua() {
        return """
                local key = KEYS[1]
                local owner = ARGV[1]
                local dataSeconds = tonumber(ARGV[2])
                local currSeq = tonumber(ARGV[3])
                local lastSeq = tonumber(ARGV[4])
                local nowMillis = tonumber(ARGV[5])
                if redis.call('EXISTS', key) == 0 then
                    -- key 已消失时直接重建缓存段，兼容缓存清理或锁过期后的修复
                    redis.call('HSET', key, 'CURR', currSeq, 'LAST', lastSeq, 'TIME', nowMillis)
                    redis.call('EXPIRE', key, dataSeconds)
                    return 1
                end
                if redis.call('HGET', key, 'LOCK') ~= owner then
                    -- 只允许锁拥有者安装新段，避免并发扩段互相覆盖
                    return 2
                end
                redis.call('HDEL', key, 'LOCK')
                redis.call('HSET', key, 'CURR', currSeq, 'LAST', lastSeq, 'TIME', nowMillis)
                redis.call('EXPIRE', key, dataSeconds)
                return 0
                """;
    }
}
