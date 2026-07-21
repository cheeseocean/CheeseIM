package com.cheeseocean.im.postman.state;

import com.cheeseocean.im.common.api.event.OfflinePushEvent;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Redis Cluster 安全的用户级节点投递结果聚合器。
 *
 * <p>每个 attempt 使用 HASH 保存冻结节点和事件，分片 ZSET 只负责发现超时项；
 * 状态 key 与 ZSET 使用相同 hash tag，所有状态迁移均由 Lua 原子完成。</p>
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisNodeDeliveryPendingStore implements NodeDeliveryPendingStore {

    private static final int SHARD_COUNT = 64;
    private static final long STATE_TTL_SECONDS = 86_400L;
    private static final String NODE_PREFIX = "node:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<String> registerScript =
            new DefaultRedisScript<>(registerLua(), String.class);
    private final DefaultRedisScript<String> outcomeScript =
            new DefaultRedisScript<>(outcomeLua(), String.class);
    private final DefaultRedisScript<String> expireScript =
            new DefaultRedisScript<>(expireLua(), String.class);
    private final DefaultRedisScript<Long> claimPublishScript =
            new DefaultRedisScript<>(claimPublishLua(), Long.class);
    private final DefaultRedisScript<Long> releasePublishScript =
            new DefaultRedisScript<>(releasePublishLua(), Long.class);
    private final DefaultRedisScript<Long> publishScript =
            new DefaultRedisScript<>(publishLua(), Long.class);

    public RedisNodeDeliveryPendingStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AttemptRef identity(String deliveryId, String userId) {
        if (deliveryId == null || deliveryId.isBlank() || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("deliveryId and userId are required");
        }
        byte[] digest = sha256(lengthSeparated(deliveryId, userId));
        return new AttemptRef(HexFormat.of().formatHex(digest), Byte.toUnsignedInt(digest[0]) % SHARD_COUNT);
    }

    @Override
    public Registration register(AttemptRef attempt,
                                 List<String> expectedNodes,
                                 OfflinePushEvent offlineEvent,
                                 long deadlineMillis) {
        if (expectedNodes == null || expectedNodes.isEmpty()) {
            throw new IllegalArgumentException("expectedNodes must not be empty");
        }
        try {
            String[] args = new String[5 + expectedNodes.size()];
            args[0] = attempt.id();
            args[1] = objectMapper.writeValueAsString(offlineEvent);
            args[2] = Long.toString(deadlineMillis);
            args[3] = Long.toString(STATE_TTL_SECONDS);
            args[4] = Integer.toString(expectedNodes.size());
            for (int i = 0; i < expectedNodes.size(); i++) {
                args[5 + i] = nodeField(expectedNodes.get(i));
            }
            String state = redisTemplate.execute(registerScript, keys(attempt), (Object[]) args);
            return Registration.valueOf(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize offline compensation event", exception);
        }
    }

    @Override
    public Outcome recordNodeOutcome(AttemptRef attempt, String gatewayNode, boolean delivered) {
        String state = redisTemplate.execute(
                outcomeScript,
                keys(attempt),
                nodeField(gatewayNode),
                delivered ? "1" : "0",
                Long.toString(STATE_TTL_SECONDS));
        return Outcome.valueOf(state);
    }

    @Override
    public Outcome expire(AttemptRef attempt, long nowMillis) {
        String state = redisTemplate.execute(
                expireScript, keys(attempt), Long.toString(nowMillis), Long.toString(STATE_TTL_SECONDS));
        return Outcome.valueOf(state);
    }

    @Override
    public List<AttemptRef> findDue(int shard, long nowMillis, int limit) {
        var ids = redisTemplate.opsForZSet().rangeByScore(
                RedisKeys.nodeDeliveryPendingDeadlines(shard), 0, nowMillis, 0, Math.max(1, limit));
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(id -> new AttemptRef(id, shard)).toList();
    }

    @Override
    public Optional<OfflinePushEvent> findOfflineEvent(AttemptRef attempt) {
        Object value = redisTemplate.opsForHash().get(stateKey(attempt), "event");
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value.toString(), OfflinePushEvent.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize offline compensation event", exception);
        }
    }

    @Override
    public boolean claimOfflinePublish(AttemptRef attempt, long nowMillis, long leaseMillis) {
        Long result = redisTemplate.execute(
                claimPublishScript,
                keys(attempt),
                Long.toString(nowMillis),
                Long.toString(nowMillis + Math.max(1_000L, leaseMillis)),
                Long.toString(STATE_TTL_SECONDS));
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public void releaseOfflinePublish(AttemptRef attempt, long retryAtMillis) {
        redisTemplate.execute(releasePublishScript, keys(attempt), Long.toString(retryAtMillis));
    }

    @Override
    public boolean markOfflinePublished(AttemptRef attempt) {
        Long result = redisTemplate.execute(publishScript, keys(attempt), Long.toString(STATE_TTL_SECONDS));
        return Long.valueOf(1L).equals(result);
    }

    private List<String> keys(AttemptRef attempt) {
        return List.of(stateKey(attempt), RedisKeys.nodeDeliveryPendingDeadlines(attempt.shard()));
    }

    private String stateKey(AttemptRef attempt) {
        return RedisKeys.nodeDeliveryPending(attempt.shard(), attempt.id());
    }

    private static String nodeField(String gatewayNode) {
        if (gatewayNode == null || gatewayNode.isBlank()) {
            throw new IllegalArgumentException("gatewayNode is required");
        }
        return NODE_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(gatewayNode.getBytes(StandardCharsets.UTF_8));
    }

    private static String lengthSeparated(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            result.append(value.length()).append(':').append(value);
        }
        return result.toString();
    }

    private static byte[] sha256(String material) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String registerLua() {
        return """
                local state = redis.call('HGET', KEYS[1], 'state')
                if state then return state end
                redis.call('HSET', KEYS[1],
                    'state', 'PENDING',
                    'event', ARGV[2],
                    'deadline', ARGV[3],
                    'expected', ARGV[5],
                    'failed', '0')
                for i = 6, #ARGV do
                    redis.call('HSET', KEYS[1], ARGV[i], 'PENDING')
                end
                redis.call('EXPIRE', KEYS[1], ARGV[4])
                redis.call('ZADD', KEYS[2], ARGV[3], ARGV[1])
                redis.call('EXPIRE', KEYS[2], ARGV[4])
                return 'NEW'
                """;
    }

    private static String outcomeLua() {
        return """
                local state = redis.call('HGET', KEYS[1], 'state')
                if not state then return 'MISSING' end
                if state == 'DELIVERED' then return 'DELIVERED' end
                if state == 'PUBLISHED' then return 'PUBLISHED' end
                if state == 'PUBLISHING' then return 'PUBLISHING' end
                if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 0 then return 'IGNORED' end
                if state == 'OFFLINE_READY' then
                    if ARGV[2] == '1' then
                        redis.call('HSET', KEYS[1], ARGV[1], 'DELIVERED', 'state', 'DELIVERED')
                        redis.call('ZREM', KEYS[2], string.match(KEYS[1], '([^:]+)$'))
                        redis.call('EXPIRE', KEYS[1], ARGV[3])
                        return 'DELIVERED'
                    end
                    return 'OFFLINE_READY'
                end
                local nodeState = redis.call('HGET', KEYS[1], ARGV[1])
                if nodeState ~= 'PENDING' then return 'WAITING' end
                if ARGV[2] == '1' then
                    redis.call('HSET', KEYS[1], ARGV[1], 'DELIVERED', 'state', 'DELIVERED')
                    redis.call('ZREM', KEYS[2], string.match(KEYS[1], '([^:]+)$'))
                    redis.call('EXPIRE', KEYS[1], ARGV[3])
                    return 'DELIVERED'
                end
                redis.call('HSET', KEYS[1], ARGV[1], 'FAILED')
                local failed = redis.call('HINCRBY', KEYS[1], 'failed', 1)
                local expected = tonumber(redis.call('HGET', KEYS[1], 'expected'))
                if failed >= expected then
                    redis.call('HSET', KEYS[1], 'state', 'OFFLINE_READY')
                    redis.call('EXPIRE', KEYS[1], ARGV[3])
                    return 'OFFLINE_READY'
                end
                return 'WAITING'
                """;
    }

    private static String expireLua() {
        return """
                local state = redis.call('HGET', KEYS[1], 'state')
                if not state then
                    redis.call('ZREM', KEYS[2], string.match(KEYS[1], '([^:]+)$'))
                    return 'MISSING'
                end
                if state == 'PUBLISHING' then
                    local leaseUntil = tonumber(redis.call('HGET', KEYS[1], 'publishLeaseUntil') or '0')
                    if leaseUntil > tonumber(ARGV[1]) then return 'WAITING' end
                    redis.call('HSET', KEYS[1], 'state', 'OFFLINE_READY')
                    return 'OFFLINE_READY'
                end
                if state ~= 'PENDING' then return state end
                local deadline = tonumber(redis.call('HGET', KEYS[1], 'deadline'))
                if deadline > tonumber(ARGV[1]) then return 'WAITING' end
                redis.call('HSET', KEYS[1], 'state', 'OFFLINE_READY')
                redis.call('EXPIRE', KEYS[1], ARGV[2])
                return 'OFFLINE_READY'
                """;
    }

    private static String claimPublishLua() {
        return """
                local state = redis.call('HGET', KEYS[1], 'state')
                if state == 'PUBLISHING' then
                    local leaseUntil = tonumber(redis.call('HGET', KEYS[1], 'publishLeaseUntil') or '0')
                    if leaseUntil > tonumber(ARGV[1]) then return 0 end
                elseif state ~= 'OFFLINE_READY' then
                    return 0
                end
                redis.call('HSET', KEYS[1], 'state', 'PUBLISHING', 'publishLeaseUntil', ARGV[2])
                redis.call('ZADD', KEYS[2], ARGV[2], string.match(KEYS[1], '([^:]+)$'))
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                return 1
                """;
    }

    private static String releasePublishLua() {
        return """
                if redis.call('HGET', KEYS[1], 'state') ~= 'PUBLISHING' then return 0 end
                redis.call('HSET', KEYS[1], 'state', 'OFFLINE_READY')
                redis.call('HDEL', KEYS[1], 'publishLeaseUntil')
                redis.call('ZADD', KEYS[2], ARGV[1], string.match(KEYS[1], '([^:]+)$'))
                return 1
                """;
    }

    private static String publishLua() {
        return """
                if redis.call('HGET', KEYS[1], 'state') ~= 'PUBLISHING' then return 0 end
                redis.call('HSET', KEYS[1], 'state', 'PUBLISHED')
                redis.call('HDEL', KEYS[1], 'publishLeaseUntil')
                redis.call('ZREM', KEYS[2], string.match(KEYS[1], '([^:]+)$'))
                redis.call('EXPIRE', KEYS[1], ARGV[1])
                return 1
                """;
    }
}
