package com.cheeseocean.im.common.core.store.session.refresh.redis;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.session.refresh.RefreshTokenCodec;
import com.cheeseocean.im.common.core.store.session.refresh.RefreshTokenStateStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Redis refresh token family 状态机。
 *
 * <p>family 的 current/used/status/session 全部位于一个 HASH，inspect/rotate/revoke
 * 均为单 key Lua，兼容 Redis Cluster 并消除并发 refresh 的 read-delete-write 窗口。</p>
 */
public class RedisRefreshTokenStateStore implements RefreshTokenStateStore {

    private static final DefaultRedisScript<Long> CREATE_SCRIPT =
            new DefaultRedisScript<>(createLua(), Long.class);
    private static final DefaultRedisScript<List> INSPECT_SCRIPT =
            new DefaultRedisScript<>(inspectLua(), List.class);
    private static final DefaultRedisScript<List> ROTATE_SCRIPT =
            new DefaultRedisScript<>(rotateLua(), List.class);
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT =
            new DefaultRedisScript<>(revokeLua(), Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    @Override
    public IssuedToken createFamily(String sessionId, long ttlMs, long nowMillis) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId required");
        }
        long stableTtlMs = Math.max(1_000L, ttlMs);
        for (int attempt = 0; attempt < 3; attempt++) {
            String familyId = RefreshTokenCodec.newFamilyId();
            String token = RefreshTokenCodec.issue(familyId);
            long expiresAt = nowMillis + stableTtlMs;
            Long created = redisTemplate.execute(
                    CREATE_SCRIPT,
                    Collections.singletonList(RedisKeys.refreshTokenFamily(familyId)),
                    sessionId,
                    RefreshTokenCodec.hash(token),
                    Long.toString(expiresAt),
                    Long.toString(stableTtlMs));
            if (Long.valueOf(1L).equals(created)) {
                return new IssuedToken(familyId, token, sessionId, expiresAt);
            }
        }
        throw new IllegalStateException("Failed to allocate unique refresh token family");
    }

    @Override
    public Inspection inspect(String refreshToken, long nowMillis) {
        String familyId = RefreshTokenCodec.familyId(refreshToken);
        if (familyId == null) {
            return invalidInspection();
        }
        List<?> result = redisTemplate.execute(
                INSPECT_SCRIPT,
                Collections.singletonList(RedisKeys.refreshTokenFamily(familyId)),
                RefreshTokenCodec.hash(refreshToken),
                Long.toString(nowMillis));
        if (result == null || result.size() < 3) {
            return invalidInspection();
        }
        return new Inspection(
                TokenStatus.valueOf(text(result.get(0))),
                familyId,
                emptyToNull(result.get(1)),
                number(result.get(2)));
    }

    @Override
    public Rotation rotate(String refreshToken, long nowMillis) {
        String familyId = RefreshTokenCodec.familyId(refreshToken);
        if (familyId == null) {
            return invalidRotation();
        }
        String nextToken = RefreshTokenCodec.issue(familyId);
        List<?> result = redisTemplate.execute(
                ROTATE_SCRIPT,
                Collections.singletonList(RedisKeys.refreshTokenFamily(familyId)),
                RefreshTokenCodec.hash(refreshToken),
                RefreshTokenCodec.hash(nextToken),
                Long.toString(nowMillis));
        if (result == null || result.size() < 4) {
            return invalidRotation();
        }
        RotationStatus status = RotationStatus.valueOf(text(result.get(0)));
        return new Rotation(
                status,
                familyId,
                emptyToNull(result.get(1)),
                status == RotationStatus.ROTATED ? nextToken : null,
                number(result.get(2)),
                number(result.get(3)));
    }

    @Override
    public void revokeFamily(String familyId) {
        if (!RefreshTokenCodec.isFamilyId(familyId)) {
            return;
        }
        redisTemplate.execute(
                REVOKE_SCRIPT,
                Collections.singletonList(RedisKeys.refreshTokenFamily(familyId)));
    }

    private static Inspection invalidInspection() {
        return new Inspection(TokenStatus.INVALID, null, null, 0L);
    }

    private static Rotation invalidRotation() {
        return new Rotation(RotationStatus.INVALID, null, null, null, 0L, 0L);
    }

    private static String text(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static String emptyToNull(Object value) {
        String result = text(value);
        return result.isBlank() ? null : result;
    }

    private static long number(Object value) {
        String result = text(value);
        return result.isBlank() ? 0L : Long.parseLong(result);
    }

    private static String createLua() {
        return """
                local key = KEYS[1]
                if redis.call('EXISTS', key) == 1 then return 0 end
                redis.call('HSET', key,
                    'sessionId', ARGV[1],
                    'status', 'ACTIVE',
                    'currentHash', ARGV[2],
                    'generation', 0,
                    'expiresAt', ARGV[3])
                redis.call('PEXPIRE', key, tonumber(ARGV[4]))
                return 1
                """;
    }

    private static String inspectLua() {
        return """
                local key = KEYS[1]
                if redis.call('EXISTS', key) == 0 then return {'INVALID', '', '0'} end
                local expiresAt = tonumber(redis.call('HGET', key, 'expiresAt') or '0')
                if expiresAt <= tonumber(ARGV[2]) then
                    redis.call('DEL', key)
                    return {'INVALID', '', '0'}
                end
                local sessionId = redis.call('HGET', key, 'sessionId') or ''
                local status = redis.call('HGET', key, 'status')
                if status == 'COMPROMISED' then
                    return {'REUSED', sessionId, tostring(expiresAt)}
                end
                if status ~= 'ACTIVE' then
                    return {'REVOKED', sessionId, tostring(expiresAt)}
                end
                if redis.call('HGET', key, 'currentHash') == ARGV[1] then
                    return {'CURRENT', sessionId, tostring(expiresAt)}
                end
                if redis.call('HEXISTS', key, 'used:' .. ARGV[1]) == 1 then
                    return {'REUSED', sessionId, tostring(expiresAt)}
                end
                return {'INVALID', '', '0'}
                """;
    }

    private static String rotateLua() {
        return """
                local key = KEYS[1]
                if redis.call('EXISTS', key) == 0 then return {'INVALID', '', '0', '0'} end
                local expiresAt = tonumber(redis.call('HGET', key, 'expiresAt') or '0')
                if expiresAt <= tonumber(ARGV[3]) then
                    redis.call('DEL', key)
                    return {'INVALID', '', '0', '0'}
                end
                local sessionId = redis.call('HGET', key, 'sessionId') or ''
                local generation = tonumber(redis.call('HGET', key, 'generation') or '0')
                if redis.call('HGET', key, 'status') ~= 'ACTIVE' then
                    return {'REVOKED', sessionId, tostring(expiresAt), tostring(generation)}
                end
                if redis.call('HGET', key, 'currentHash') == ARGV[1] then
                    redis.call('HSET', key,
                        'used:' .. ARGV[1], generation,
                        'currentHash', ARGV[2],
                        'generation', generation + 1)
                    return {'ROTATED', sessionId, tostring(expiresAt), tostring(generation + 1)}
                end
                if redis.call('HEXISTS', key, 'used:' .. ARGV[1]) == 1 then
                    redis.call('HSET', key, 'status', 'COMPROMISED')
                    return {'REUSED', sessionId, tostring(expiresAt), tostring(generation)}
                end
                return {'INVALID', '', '0', '0'}
                """;
    }

    private static String revokeLua() {
        return """
                local key = KEYS[1]
                if redis.call('EXISTS', key) == 0 then return 0 end
                redis.call('HSET', key, 'status', 'REVOKED')
                return 1
                """;
    }
}
