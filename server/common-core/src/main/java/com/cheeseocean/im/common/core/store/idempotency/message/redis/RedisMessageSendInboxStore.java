package com.cheeseocean.im.common.core.store.idempotency.message.redis;

import com.cheeseocean.im.common.core.store.idempotency.message.MessageSendInboxProperties;
import com.cheeseocean.im.common.core.store.idempotency.message.MessageSendInboxStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于单 Redis HASH 的消息发送 inbox，所有状态迁移均由 Lua 原子完成。
 */
public class RedisMessageSendInboxStore implements MessageSendInboxStore {

    private static final DefaultRedisScript<List> CLAIM_SCRIPT =
            new DefaultRedisScript<>(claimLua(), List.class);
    private static final DefaultRedisScript<List> ACCEPT_SCRIPT =
            new DefaultRedisScript<>(acceptLua(), List.class);
    private static final DefaultRedisScript<Long> BIND_OFFLINE_PUSH_SCRIPT =
            new DefaultRedisScript<>(bindOfflinePushLua(), Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(releaseLua(), Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;
    private final long leaseMillis;

    public RedisMessageSendInboxStore(StringRedisTemplate redisTemplate,
                                      MessageSendInboxProperties properties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        Objects.requireNonNull(properties, "properties");
        this.ttlSeconds = properties.normalizedTtlSeconds();
        this.leaseMillis = properties.normalizedLeaseMillis();
    }

    @Override
    public Claim claim(String key,
                       String payloadFingerprint,
                       String proposedServerMsgId,
                       String ownerToken,
                       long nowMillis) {
        List<?> result = redisTemplate.execute(
                CLAIM_SCRIPT,
                Collections.singletonList(key),
                payloadFingerprint,
                proposedServerMsgId,
                ownerToken,
                Long.toString(nowMillis),
                Long.toString(nowMillis + leaseMillis),
                Long.toString(ttlSeconds));
        if (result == null || result.size() < 5) {
            throw new IllegalStateException("Message send inbox claim returned invalid result");
        }
        return new Claim(
                ClaimStatus.valueOf(String.valueOf(result.get(0))),
                emptyToNull(result.get(1)),
                parseLong(result.get(2)),
                parseLong(result.get(3)),
                parseNullableBoolean(result.get(4)));
    }

    @Override
    public boolean bindEffectiveOfflinePush(String key,
                                            String ownerToken,
                                            boolean needOfflinePush) {
        Long result = redisTemplate.execute(
                BIND_OFFLINE_PUSH_SCRIPT,
                Collections.singletonList(key),
                ownerToken,
                needOfflinePush ? "1" : "0",
                Long.toString(ttlSeconds));
        if (result == null || result < 0L) {
            throw new IllegalStateException("Message send inbox policy binding lost its active lease");
        }
        return result == 1L;
    }

    @Override
    public long markAccepted(String key,
                             String payloadFingerprint,
                             String serverMsgId,
                             long acceptedAt) {
        List<?> result = redisTemplate.execute(
                ACCEPT_SCRIPT,
                Collections.singletonList(key),
                payloadFingerprint,
                serverMsgId,
                Long.toString(acceptedAt),
                Long.toString(ttlSeconds));
        if (result == null || result.size() < 2 || parseLong(result.get(0)) != 1L) {
            throw new IllegalStateException("Message send inbox disappeared or changed before broker ACK");
        }
        return parseLong(result.get(1));
    }

    @Override
    public void release(String key, String ownerToken) {
        redisTemplate.execute(
                RELEASE_SCRIPT,
                Collections.singletonList(key),
                ownerToken,
                Long.toString(ttlSeconds));
    }

    private static long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String emptyToNull(Object value) {
        String text = value == null ? null : String.valueOf(value);
        return text == null || text.isBlank() ? null : text;
    }

    private static Boolean parseNullableBoolean(Object value) {
        long number = parseLong(value);
        return number < 0L ? null : number == 1L;
    }

    private static String claimLua() {
        return """
                local key = KEYS[1]
                local fingerprint = ARGV[1]
                local proposedServerMsgId = ARGV[2]
                local owner = ARGV[3]
                local now = tonumber(ARGV[4])
                local leaseUntil = tonumber(ARGV[5])
                local ttlSeconds = tonumber(ARGV[6])

                if redis.call('EXISTS', key) == 0 then
                    redis.call('HSET', key,
                        'fingerprint', fingerprint,
                        'serverMsgId', proposedServerMsgId,
                        'status', 'PENDING',
                        'owner', owner,
                        'leaseUntil', leaseUntil,
                        'createdAt', now,
                        'acceptedAt', 0,
                        'offlinePush', -1)
                    redis.call('EXPIRE', key, ttlSeconds)
                    return {'ACQUIRED', proposedServerMsgId, tostring(now), '0', '-1'}
                end

                local storedFingerprint = redis.call('HGET', key, 'fingerprint')
                local storedServerMsgId = redis.call('HGET', key, 'serverMsgId') or ''
                local createdAt = redis.call('HGET', key, 'createdAt') or '0'
                local acceptedAt = redis.call('HGET', key, 'acceptedAt') or '0'
                local offlinePush = redis.call('HGET', key, 'offlinePush') or '-1'
                if storedFingerprint ~= fingerprint then
                    return {'CONFLICT', storedServerMsgId, createdAt, acceptedAt, offlinePush}
                end
                if redis.call('HGET', key, 'status') == 'ACCEPTED' then
                    redis.call('EXPIRE', key, ttlSeconds)
                    return {'ACCEPTED', storedServerMsgId, createdAt, acceptedAt, offlinePush}
                end

                local storedLeaseUntil = tonumber(redis.call('HGET', key, 'leaseUntil') or '0')
                if storedLeaseUntil <= now then
                    redis.call('HSET', key, 'owner', owner, 'leaseUntil', leaseUntil)
                    redis.call('EXPIRE', key, ttlSeconds)
                    return {'ACQUIRED', storedServerMsgId, createdAt, '0', offlinePush}
                end
                return {'IN_PROGRESS', storedServerMsgId, createdAt, '0', offlinePush}
                """;
    }

    private static String bindOfflinePushLua() {
        return """
                local key = KEYS[1]
                if redis.call('EXISTS', key) == 0 then return -1 end
                if redis.call('HGET', key, 'status') ~= 'PENDING' then return -1 end
                if redis.call('HGET', key, 'owner') ~= ARGV[1] then return -1 end
                local stored = tonumber(redis.call('HGET', key, 'offlinePush') or '-1')
                if stored < 0 then
                    stored = tonumber(ARGV[2])
                    redis.call('HSET', key, 'offlinePush', stored)
                end
                redis.call('EXPIRE', key, tonumber(ARGV[3]))
                return stored
                """;
    }

    private static String acceptLua() {
        return """
                local key = KEYS[1]
                if redis.call('EXISTS', key) == 0 then return {0, 0} end
                if redis.call('HGET', key, 'fingerprint') ~= ARGV[1] then return {-1, 0} end
                if redis.call('HGET', key, 'serverMsgId') ~= ARGV[2] then return {-1, 0} end

                local acceptedAt = tonumber(redis.call('HGET', key, 'acceptedAt') or '0')
                if acceptedAt == 0 then acceptedAt = tonumber(ARGV[3]) end
                redis.call('HSET', key,
                    'status', 'ACCEPTED',
                    'acceptedAt', acceptedAt,
                    'owner', '',
                    'leaseUntil', 0)
                redis.call('EXPIRE', key, tonumber(ARGV[4]))
                return {1, acceptedAt}
                """;
    }

    private static String releaseLua() {
        return """
                local key = KEYS[1]
                if redis.call('EXISTS', key) == 0 then return 0 end
                if redis.call('HGET', key, 'status') ~= 'PENDING' then return 0 end
                if redis.call('HGET', key, 'owner') ~= ARGV[1] then return 0 end
                redis.call('HSET', key, 'owner', '', 'leaseUntil', 0)
                redis.call('EXPIRE', key, tonumber(ARGV[2]))
                return 1
                """;
    }
}
