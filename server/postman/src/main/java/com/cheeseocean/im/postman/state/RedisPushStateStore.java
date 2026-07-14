package com.cheeseocean.im.postman.state;

import com.cheeseocean.im.common.api.enums.DeliveryState;
import com.cheeseocean.im.postman.entity.PushAttempt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 Redis 的离线推送状态存储。
 *
 * <p>每个 serverMsgId 使用一个 HASH，Lua 在同一 Redis key 内先检查已确认/已读状态，
 * 再检查并创建用户的推送尝试。这样多个 postman 副本只能有一个取得厂商推送资格。
 */
@Service
public class RedisPushStateStore implements PushStateStore {

    private static final String PUSH_STATE_KEY_PREFIX = "cheese_im:push_state:";
    private static final String DAILY_QUOTA_KEY_PREFIX = "cheese_im:push_daily_quota:";
    private static final String ATTEMPT_FIELD_PREFIX = "attempt:";
    private static final String STATE_FIELD_PREFIX = "state:";

    private final StringRedisTemplate redisTemplate;
    private final long stateTtlSeconds;
    private final DefaultRedisScript<List> claimPushScript;
    private final DefaultRedisScript<Long> cancelAttemptScript;
    private final DefaultRedisScript<Long> recordStateScript;
    private final DefaultRedisScript<Long> claimDailyQuotaScript;
    private final DefaultRedisScript<Long> releaseDailyQuotaScript;

    public RedisPushStateStore(StringRedisTemplate redisTemplate,
                               @Value("${cheeseim.push-state.ttl-seconds:86400}") long stateTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.stateTtlSeconds = Math.max(1L, stateTtlSeconds);
        this.claimPushScript = new DefaultRedisScript<>(claimPushLua(), List.class);
        this.cancelAttemptScript = new DefaultRedisScript<>(cancelAttemptLua(), Long.class);
        this.recordStateScript = new DefaultRedisScript<>(recordStateLua(), Long.class);
        this.claimDailyQuotaScript = new DefaultRedisScript<>(claimDailyQuotaLua(), Long.class);
        this.releaseDailyQuotaScript = new DefaultRedisScript<>(releaseDailyQuotaLua(), Long.class);
    }

    @Override
    public PushClaim claimPush(String serverMsgId, String userId) {
        if (isBlank(serverMsgId) || isBlank(userId)) {
            return new PushClaim(null, DeliveryState.INBOXED, true);
        }
        long now = System.currentTimeMillis();
        List<?> result = redisTemplate.execute(claimPushScript, Collections.singletonList(stateKey(serverMsgId)),
                ATTEMPT_FIELD_PREFIX + userId, STATE_FIELD_PREFIX + userId, Long.toString(now),
                Long.toString(stateTtlSeconds), DeliveryState.INBOXED.name(),
                DeliveryState.ONLINE_CONFIRMED.name(), DeliveryState.READ.name());
        if (result == null || result.size() < 3) {
            return new PushClaim(null, DeliveryState.INBOXED, true);
        }
        boolean claimed = asLong(result.get(0)) == 1L;
        DeliveryState state = deliveryState(String.valueOf(result.get(1)));
        boolean duplicate = asLong(result.get(2)) == 1L;
        return claimed
                ? new PushClaim(new PushAttempt(serverMsgId, userId, Instant.ofEpochMilli(now), false), state, false)
                : new PushClaim(null, state, duplicate);
    }

    @Override
    public void cancelAttempt(String serverMsgId, String userId) {
        if (isBlank(serverMsgId) || isBlank(userId)) {
            return;
        }
        redisTemplate.execute(cancelAttemptScript, Collections.singletonList(stateKey(serverMsgId)),
                ATTEMPT_FIELD_PREFIX + userId, Long.toString(System.currentTimeMillis()), Long.toString(stateTtlSeconds));
    }

    @Override
    public void recordDeliveryState(String serverMsgId, String userId, DeliveryState state) {
        if (isBlank(serverMsgId) || isBlank(userId) || state == null) {
            return;
        }
        redisTemplate.execute(recordStateScript, Collections.singletonList(stateKey(serverMsgId)),
                STATE_FIELD_PREFIX + userId, state.name(), Long.toString(stateTtlSeconds));
    }

    @Override
    public Optional<PushAttempt> findAttempt(String serverMsgId, String userId) {
        if (isBlank(serverMsgId) || isBlank(userId)) {
            return Optional.empty();
        }
        String value = (String) redisTemplate.opsForHash().get(stateKey(serverMsgId), ATTEMPT_FIELD_PREFIX + userId);
        return parseAttempt(serverMsgId, userId, value);
    }

    @Override
    public Optional<PushAttempt> findAnyAttempt(String serverMsgId) {
        if (isBlank(serverMsgId)) {
            return Optional.empty();
        }
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(stateKey(serverMsgId));
        for (Map.Entry<Object, Object> entry : fields.entrySet()) {
            String field = String.valueOf(entry.getKey());
            if (field.startsWith(ATTEMPT_FIELD_PREFIX)) {
                Optional<PushAttempt> attempt = parseAttempt(serverMsgId,
                        field.substring(ATTEMPT_FIELD_PREFIX.length()), String.valueOf(entry.getValue()));
                if (attempt.isPresent()) {
                    return attempt;
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean claimDailyQuota(String userId, int maxDailyCount) {
        if (isBlank(userId) || maxDailyCount <= 0) {
            return false;
        }
        Long result = redisTemplate.execute(claimDailyQuotaScript, Collections.singletonList(dailyQuotaKey(userId)),
                Integer.toString(maxDailyCount), Long.toString(secondsUntilNextUtcDay()));
        return result != null && result == 1L;
    }

    @Override
    public void releaseDailyQuota(String userId) {
        if (isBlank(userId)) {
            return;
        }
        redisTemplate.execute(releaseDailyQuotaScript, Collections.singletonList(dailyQuotaKey(userId)));
    }

    @Override
    public int getDailyPushCount(String userId) {
        if (isBlank(userId)) {
            return 0;
        }
        String value = redisTemplate.opsForValue().get(dailyQuotaKey(userId));
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Optional<PushAttempt> parseAttempt(String serverMsgId, String userId, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", -1);
        try {
            return Optional.of(new PushAttempt(serverMsgId, userId, Instant.ofEpochMilli(Long.parseLong(parts[0])),
                    parts.length > 1 && "1".equals(parts[1])));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private DeliveryState deliveryState(String value) {
        try {
            return DeliveryState.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return DeliveryState.INBOXED;
        }
    }

    private long secondsUntilNextUtcDay() {
        long now = System.currentTimeMillis();
        long nextDay = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        return Math.max(1L, (nextDay - now + 999L) / 1_000L);
    }

    private String stateKey(String serverMsgId) {
        return PUSH_STATE_KEY_PREFIX + serverMsgId;
    }

    private String dailyQuotaKey(String userId) {
        return DAILY_QUOTA_KEY_PREFIX + LocalDate.now(ZoneOffset.UTC) + ":" + userId;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private String claimPushLua() {
        return """
                local state = redis.call('HGET', KEYS[1], ARGV[2])
                if state == ARGV[6] or state == ARGV[7] then
                    return {0, state, 0}
                end
                if redis.call('HGET', KEYS[1], ARGV[1]) then
                    return {0, state or ARGV[5], 1}
                end
                redis.call('HSET', KEYS[1], ARGV[1], ARGV[3] .. '|0')
                redis.call('EXPIRE', KEYS[1], ARGV[4])
                return {1, state or ARGV[5], 0}
                """;
    }

    private String cancelAttemptLua() {
        return """
                local current = redis.call('HGET', KEYS[1], ARGV[1])
                if current then
                    local separator = string.find(current, '|')
                    local createdAt = separator and string.sub(current, 1, separator - 1) or ARGV[2]
                    redis.call('HSET', KEYS[1], ARGV[1], createdAt .. '|1')
                else
                    redis.call('HSET', KEYS[1], ARGV[1], ARGV[2] .. '|1')
                end
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                return 1
                """;
    }

    private String recordStateLua() {
        return """
                redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                return 1
                """;
    }

    private String claimDailyQuotaLua() {
        return """
                local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                if current >= tonumber(ARGV[1]) then
                    return 0
                end
                local next = redis.call('INCR', KEYS[1])
                if next == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[2])
                end
                return 1
                """;
    }

    private String releaseDailyQuotaLua() {
        return """
                local current = tonumber(redis.call('GET', KEYS[1]) or '0')
                if current <= 0 then
                    return 0
                end
                return redis.call('DECR', KEYS[1])
                """;
    }
}
