package com.cheeseocean.im.common.core.store.idempotency.ingress.redis;

import com.cheeseocean.im.common.core.store.idempotency.ingress.IngressMessageInboxProperties;
import com.cheeseocean.im.common.core.store.idempotency.ingress.IngressMessageInboxStore;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Redis ingress inbox。
 *
 * <p>每条消息使用一个 HASH，单 key Lua 保证状态迁移原子；批量方法通过 pipeline
 * 合并网络往返，同时保持 Redis Cluster 跨槽兼容。</p>
 */
public class RedisIngressMessageInboxStore implements IngressMessageInboxStore {

    private static final DefaultRedisScript<List> CLAIM_SCRIPT =
            new DefaultRedisScript<>(claimLua(), List.class);
    private static final DefaultRedisScript<Long> BIND_SEQ_SCRIPT =
            new DefaultRedisScript<>(bindSeqLua(), Long.class);
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT =
            new DefaultRedisScript<>(completeLua(), Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(releaseLua(), Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;
    private final long leaseMillis;

    public RedisIngressMessageInboxStore(StringRedisTemplate redisTemplate,
                                         IngressMessageInboxProperties properties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        Objects.requireNonNull(properties, "properties");
        ttlSeconds = properties.normalizedTtlSeconds();
        leaseMillis = properties.normalizedLeaseMillis();
    }

    @Override
    public List<Claim> claimBatch(List<ClaimRequest> requests,
                                  String ownerToken,
                                  long nowMillis) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<Object> raw = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                for (ClaimRequest request : requests) {
                    operations.execute(
                            CLAIM_SCRIPT,
                            Collections.singletonList(request.key()),
                            request.payloadFingerprint(),
                            ownerToken,
                            Long.toString(nowMillis),
                            Long.toString(nowMillis + leaseMillis),
                            Long.toString(ttlSeconds));
                }
                return null;
            }
        });
        requireResultCount(raw, requests.size(), "claim");
        List<Claim> claims = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            Object item = raw.get(index);
            if (!(item instanceof List<?> values) || values.size() < 3) {
                throw new IllegalStateException("Ingress inbox claim returned invalid pipeline item");
            }
            claims.add(new Claim(
                    requests.get(index).key(),
                    ClaimStatus.valueOf(text(values.get(0))),
                    number(values.get(1)),
                    number(values.get(2))));
        }
        return claims;
    }

    @Override
    public Map<String, Long> bindSequences(List<SequenceBinding> bindings,
                                           String ownerToken) {
        if (bindings == null || bindings.isEmpty()) {
            return Map.of();
        }
        List<Object> raw = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                for (SequenceBinding binding : bindings) {
                    operations.execute(
                            BIND_SEQ_SCRIPT,
                            Collections.singletonList(binding.key()),
                            ownerToken,
                            Long.toString(binding.proposedSeq()),
                            Long.toString(ttlSeconds));
                }
                return null;
            }
        });
        requireResultCount(raw, bindings.size(), "bind seq");
        Map<String, Long> result = new LinkedHashMap<>();
        for (int index = 0; index < bindings.size(); index++) {
            long stableSeq = number(raw.get(index));
            if (stableSeq <= 0L) {
                throw new IllegalStateException("Ingress inbox lost active lease while binding seq");
            }
            result.put(bindings.get(index).key(), stableSeq);
        }
        return result;
    }

    @Override
    public void completeBatch(List<String> keys, String ownerToken) {
        transition(keys, ownerToken, COMPLETE_SCRIPT, true, "complete");
    }

    @Override
    public void releaseBatch(List<String> keys, String ownerToken) {
        transition(keys, ownerToken, RELEASE_SCRIPT, false, "release");
    }

    private void transition(List<String> keys,
                            String ownerToken,
                            DefaultRedisScript<Long> script,
                            boolean requireSuccess,
                            String operation) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        List<Object> raw = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object execute(RedisOperations operations) {
                for (String key : keys) {
                    operations.execute(
                            script,
                            Collections.singletonList(key),
                            ownerToken,
                            Long.toString(ttlSeconds));
                }
                return null;
            }
        });
        requireResultCount(raw, keys.size(), operation);
        if (requireSuccess && raw.stream().mapToLong(RedisIngressMessageInboxStore::number).anyMatch(value -> value != 1L)) {
            throw new IllegalStateException("Ingress inbox lost active lease while completing batch");
        }
    }

    private static void requireResultCount(List<Object> results, int expected, String operation) {
        if (results == null || results.size() != expected) {
            throw new IllegalStateException(
                    "Ingress inbox " + operation + " returned invalid pipeline result count");
        }
    }

    private static long number(Object value) {
        return Long.parseLong(text(value));
    }

    private static String text(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static String claimLua() {
        return """
                local key = KEYS[1]
                local fingerprint = ARGV[1]
                local owner = ARGV[2]
                local now = tonumber(ARGV[3])
                local leaseUntil = tonumber(ARGV[4])
                local ttlSeconds = tonumber(ARGV[5])

                if redis.call('EXISTS', key) == 0 then
                    redis.call('HSET', key,
                        'fingerprint', fingerprint,
                        'status', 'PROCESSING',
                        'owner', owner,
                        'leaseUntil', leaseUntil,
                        'assignedSeq', 0)
                    redis.call('EXPIRE', key, ttlSeconds)
                    return {'ACQUIRED', '0', tostring(leaseUntil)}
                end
                local assignedSeq = redis.call('HGET', key, 'assignedSeq') or '0'
                local storedLeaseUntil = tonumber(redis.call('HGET', key, 'leaseUntil') or '0')
                if redis.call('HGET', key, 'fingerprint') ~= fingerprint then
                    return {'CONFLICT', assignedSeq, tostring(storedLeaseUntil)}
                end
                if redis.call('HGET', key, 'status') == 'COMPLETED' then
                    redis.call('EXPIRE', key, ttlSeconds)
                    return {'COMPLETED', assignedSeq, '0'}
                end
                if redis.call('HGET', key, 'owner') == owner or storedLeaseUntil <= now then
                    redis.call('HSET', key, 'owner', owner, 'leaseUntil', leaseUntil)
                    redis.call('EXPIRE', key, ttlSeconds)
                    return {'ACQUIRED', assignedSeq, tostring(leaseUntil)}
                end
                return {'IN_PROGRESS', assignedSeq, tostring(storedLeaseUntil)}
                """;
    }

    private static String bindSeqLua() {
        return """
                local key = KEYS[1]
                if redis.call('EXISTS', key) == 0 then return -1 end
                if redis.call('HGET', key, 'status') ~= 'PROCESSING' then return -1 end
                if redis.call('HGET', key, 'owner') ~= ARGV[1] then return -1 end
                local assignedSeq = tonumber(redis.call('HGET', key, 'assignedSeq') or '0')
                if assignedSeq == 0 then
                    assignedSeq = tonumber(ARGV[2])
                    if assignedSeq <= 0 then return -1 end
                    redis.call('HSET', key, 'assignedSeq', assignedSeq)
                end
                redis.call('EXPIRE', key, tonumber(ARGV[3]))
                return assignedSeq
                """;
    }

    private static String completeLua() {
        return """
                local key = KEYS[1]
                if redis.call('EXISTS', key) == 0 then return 0 end
                if redis.call('HGET', key, 'status') ~= 'PROCESSING' then return 0 end
                if redis.call('HGET', key, 'owner') ~= ARGV[1] then return 0 end
                redis.call('HSET', key, 'status', 'COMPLETED', 'owner', '', 'leaseUntil', 0)
                redis.call('EXPIRE', key, tonumber(ARGV[2]))
                return 1
                """;
    }

    private static String releaseLua() {
        return """
                local key = KEYS[1]
                if redis.call('EXISTS', key) == 0 then return 0 end
                if redis.call('HGET', key, 'status') ~= 'PROCESSING' then return 0 end
                if redis.call('HGET', key, 'owner') ~= ARGV[1] then return 0 end
                redis.call('HSET', key, 'owner', '', 'leaseUntil', 0)
                redis.call('EXPIRE', key, tonumber(ARGV[2]))
                return 1
                """;
    }
}
