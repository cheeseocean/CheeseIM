package com.cheeseocean.im.common.core.store.delivery.redis;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.delivery.DeliveryStateStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Objects;

/** Redis Lua 原子推进设备送达高水位。 */
public class RedisDeliveryStateStore implements DeliveryStateStore {
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1])) or 0
            local requested = tonumber(ARGV[1]) or 0
            if requested <= current then return {current, 0} end
            redis.call('SET', KEYS[1], requested, 'EX', tonumber(ARGV[2]))
            return {requested, 1}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;

    public RedisDeliveryStateStore(StringRedisTemplate redisTemplate) {
        this(redisTemplate, 30L * 24 * 60 * 60);
    }

    public RedisDeliveryStateStore(StringRedisTemplate redisTemplate, long ttlSeconds) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.ttlSeconds = Math.max(60L, ttlSeconds);
    }

    @Override
    public AdvanceResult advance(String userId, String deviceId, String conversationId, long requestedSeq) {
        List<?> result = redisTemplate.execute(SCRIPT,
                List.of(RedisKeys.deviceDeliveredSeq(userId, deviceId, conversationId)),
                String.valueOf(requestedSeq), String.valueOf(ttlSeconds));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Redis delivery state returned an invalid result");
        }
        return new AdvanceResult(Long.parseLong(String.valueOf(result.get(0))),
                Long.parseLong(String.valueOf(result.get(1))) == 1L);
    }
}
