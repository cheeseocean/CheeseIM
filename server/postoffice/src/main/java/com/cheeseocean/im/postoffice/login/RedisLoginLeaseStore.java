package com.cheeseocean.im.postoffice.login;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.connection.MultiLoginStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis Cluster 单槽全局登录 lease 实现。
 *
 * <p>每个用户使用 active ZSET + metadata HASH；claim 在一段 Lua 内完成过期清理、冲突选择、
 * 全局限额、generation 分配和状态替换。</p>
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisLoginLeaseStore implements LoginLeaseStore {

    private static final DefaultRedisScript<String> CLAIM =
            new DefaultRedisScript<>(claimLua(), String.class);
    private static final byte[] RENEW = renewLua().getBytes(StandardCharsets.UTF_8);
    private static final byte[] RELEASE = releaseLua().getBytes(StandardCharsets.UTF_8);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long leaseTtlMs;
    private final long keyTtlMs;

    public RedisLoginLeaseStore(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                ServerProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.leaseTtlMs = Math.max(30_000L, properties.getLoginLease().getTtlMs());
        this.keyTtlMs = leaseTtlMs + Math.max(10_000L, properties.getLoginLease().getKeyGraceMs());
    }

    @Override
    public LoginLeaseClaim claim(LoginLease requested,
                                 MultiLoginStrategy strategy,
                                 int maxConnections) {
        long now = System.currentTimeMillis();
        LoginLease candidate = new LoginLease(
                requested.tenantId(),
                requested.userId(),
                requested.connectionId(),
                0L,
                requested.gatewayNode(),
                requested.deviceId(),
                requested.platformId(),
                requested.platformClass(),
                requested.sessionId(),
                now + leaseTtlMs);
        try {
            String result = redisTemplate.execute(
                    CLAIM,
                    keys(candidate),
                    Long.toString(now),
                    Long.toString(candidate.expireAt()),
                    Long.toString(keyTtlMs),
                    Integer.toString(Math.max(1, maxConnections)),
                    strategy.name(),
                    objectMapper.writeValueAsString(candidate));
            return parseClaim(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize login lease", exception);
        }
    }

    @Override
    public Set<String> renewBatch(List<LoginLeaseRenewal> renewals) {
        if (renewals == null || renewals.isEmpty()) {
            return Set.of();
        }
        long expireAt = System.currentTimeMillis() + leaseTtlMs;
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (LoginLeaseRenewal renewal : renewals) {
                connection.scriptingCommands().eval(
                        RENEW,
                        ReturnType.INTEGER,
                        2,
                        bytes(RedisKeys.loginLeaseActive(renewal.tenantId(), renewal.userId())),
                        bytes(RedisKeys.loginLeaseMetadata(renewal.tenantId(), renewal.userId())),
                        bytes(renewal.connectionId()),
                        bytes(Long.toString(renewal.generation())),
                        bytes(Long.toString(expireAt)),
                        bytes(Long.toString(keyTtlMs)));
            }
            return null;
        });
        Set<String> fenced = new LinkedHashSet<>();
        for (int i = 0; i < renewals.size(); i++) {
            Object result = i < results.size() ? results.get(i) : null;
            if (!(result instanceof Number number) || number.longValue() != 1L) {
                fenced.add(renewals.get(i).connectionId());
            }
        }
        return fenced;
    }

    @Override
    public void release(LoginLeaseRenewal renewal) {
        if (renewal == null) {
            return;
        }
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            evalRelease(connection, renewal);
            return null;
        });
    }

    @Override
    public void releaseBatch(List<LoginLeaseRenewal> renewals) {
        if (renewals == null || renewals.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (LoginLeaseRenewal renewal : renewals) {
                evalRelease(connection, renewal);
            }
            return null;
        });
    }

    private void evalRelease(org.springframework.data.redis.connection.RedisConnection connection,
                             LoginLeaseRenewal renewal) {
        connection.scriptingCommands().eval(
                RELEASE,
                ReturnType.INTEGER,
                2,
                bytes(RedisKeys.loginLeaseActive(renewal.tenantId(), renewal.userId())),
                bytes(RedisKeys.loginLeaseMetadata(renewal.tenantId(), renewal.userId())),
                bytes(renewal.connectionId()),
                bytes(Long.toString(renewal.generation())));
    }

    private LoginLeaseClaim parseClaim(String result) throws JsonProcessingException {
        if (result == null || result.isBlank()) {
            throw new IllegalStateException("Empty login lease claim result");
        }
        JsonNode root = objectMapper.readTree(result);
        String code = root.path("code").asText();
        if ("REJECTED_LIMIT".equals(code)) {
            return new LoginLeaseClaim(LoginLeaseClaim.Status.REJECTED_LIMIT, 0L, List.of());
        }
        if (!"ACCEPTED".equals(code)) {
            throw new IllegalStateException("Unknown login lease claim result: " + code);
        }
        List<LoginLease> evicted = new ArrayList<>();
        for (JsonNode node : root.path("evicted")) {
            evicted.add(objectMapper.treeToValue(node, LoginLease.class));
        }
        return new LoginLeaseClaim(
                LoginLeaseClaim.Status.ACCEPTED,
                root.path("generation").asLong(),
                List.copyOf(evicted));
    }

    private List<String> keys(LoginLease lease) {
        return List.of(
                RedisKeys.loginLeaseActive(lease.tenantId(), lease.userId()),
                RedisKeys.loginLeaseMetadata(lease.tenantId(), lease.userId()));
    }

    private byte[] bytes(String value) {
        return redisTemplate.getStringSerializer().serialize(value);
    }

    private static String claimLua() {
        return """
                local active = KEYS[1]
                local meta = KEYS[2]
                local now = tonumber(ARGV[1])
                local expireAt = tonumber(ARGV[2])
                local keyTtl = tonumber(ARGV[3])
                local maxConnections = tonumber(ARGV[4])
                local strategy = ARGV[5]
                local requested = cjson.decode(ARGV[6])

                local expired = redis.call('ZRANGEBYSCORE', active, '-inf', now)
                for _, connectionId in ipairs(expired) do
                    redis.call('ZREM', active, connectionId)
                    redis.call('HDEL', meta, connectionId)
                end

                local victims = {}
                local victimIds = {}
                local members = redis.call('ZRANGE', active, 0, -1)
                for _, connectionId in ipairs(members) do
                    local encoded = redis.call('HGET', meta, connectionId)
                    if not encoded then
                        redis.call('ZREM', active, connectionId)
                    else
                        local current = cjson.decode(encoded)
                        local conflict = current.deviceId == requested.deviceId
                        if not conflict and strategy == 'SAME_TERMINAL_KICK' then
                            conflict = current.platformId == requested.platformId
                        elseif not conflict and strategy == 'SAME_CLASS_KICK' then
                            conflict = current.platformClass == requested.platformClass
                        elseif not conflict and strategy == 'PC_AND_OTHER'
                                and requested.platformClass ~= 'PC' then
                            conflict = current.platformId == requested.platformId
                        end
                        if conflict then
                            table.insert(victims, current)
                            victimIds[connectionId] = true
                        end
                    end
                end

                local survivorCount = 0
                for _, connectionId in ipairs(members) do
                    if not victimIds[connectionId] and redis.call('HEXISTS', meta, connectionId) == 1 then
                        survivorCount = survivorCount + 1
                    end
                end
                if survivorCount + 1 > maxConnections then
                    return cjson.encode({code='REJECTED_LIMIT'})
                end

                for connectionId, _ in pairs(victimIds) do
                    redis.call('ZREM', active, connectionId)
                    redis.call('HDEL', meta, connectionId)
                end
                local generation = redis.call('HINCRBY', meta, '__epoch', 1)
                requested.generation = generation
                requested.expireAt = expireAt
                redis.call('HSET', meta, requested.connectionId, cjson.encode(requested))
                redis.call('ZADD', active, expireAt, requested.connectionId)
                redis.call('PEXPIRE', active, keyTtl)
                redis.call('PEXPIRE', meta, keyTtl)
                return cjson.encode({code='ACCEPTED', generation=generation, evicted=victims})
                """;
    }

    private static String renewLua() {
        return """
                local encoded = redis.call('HGET', KEYS[2], ARGV[1])
                if not encoded then return 0 end
                local current = cjson.decode(encoded)
                if tonumber(current.generation) ~= tonumber(ARGV[2]) then return 0 end
                if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then return 0 end
                current.expireAt = tonumber(ARGV[3])
                redis.call('HSET', KEYS[2], ARGV[1], cjson.encode(current))
                redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
                redis.call('PEXPIRE', KEYS[1], ARGV[4])
                redis.call('PEXPIRE', KEYS[2], ARGV[4])
                return 1
                """;
    }

    private static String releaseLua() {
        return """
                local encoded = redis.call('HGET', KEYS[2], ARGV[1])
                if not encoded then return 0 end
                local current = cjson.decode(encoded)
                if tonumber(current.generation) ~= tonumber(ARGV[2]) then return 0 end
                redis.call('ZREM', KEYS[1], ARGV[1])
                redis.call('HDEL', KEYS[2], ARGV[1])
                if redis.call('ZCARD', KEYS[1]) == 0 then
                    redis.call('DEL', KEYS[1])
                    if redis.call('HLEN', KEYS[2]) <= 1 then redis.call('DEL', KEYS[2]) end
                end
                return 1
                """;
    }
}
