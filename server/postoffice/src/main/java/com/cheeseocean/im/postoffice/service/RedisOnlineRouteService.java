package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 在线路由表实现：基于 Redis HASH + 单脚本 Lua 完成注册/刷新/踢下线的原子操作。
 *
 * <p>存储结构（单 key 多 field 的 Redis HASH）：
 * <ul>
 *   <li>Key：{@code online:user:{userId}}，集中一个用户所有在线设备的路由记录</li>
 *   <li>Field {@code route:{deviceId}}：JSON 形式的 {@link RouteSnapshot}（不包含心跳实时字段）</li>
 *   <li>Field {@code heartbeat:{deviceId}}：合并批刷后的心跳时间戳，避免每次刷新整条 JSON</li>
 *   <li>Field {@code connection:{deviceId}}：当前连接身份，拒绝旧连接迟到刷新或注销</li>
 *   <li>Key 整体 TTL：30 分钟，每次 register/批量 refresh/unregister 都重新 EXPIRE</li>
 * </ul>
 *
 * <p>这样设计的原因：
 * <ol>
 *   <li>路由注册/刷新/踢下线是高频写路径，旧本地缓存路径走「读-改-写」非原子，
 *       并发设备注册或心跳刷新会出现互相覆盖丢路由（ASSESSMENT P0-3）。改为单脚本 Lua 把
 *       读改写收敛到 Redis 单线程，彻底消除竞态。</li>
 *   <li>路由表本就是「跨节点共享真相」，不能再走 L1 本地缓存——L1 当前无广播失效广播（ASSESSMENT 已知缺陷），
 *       把路由放回 L1 会让多节点路由表 1-5min 不一致。因此本实现不依赖通用缓存，
 *       读写都直连 Redis。</li>
 *   <li>心跳字段独立存放，避免每次心跳都要做 JSON 反序列化/重序列化，也避免 Lua 解析 JSON 的脆弱性。</li>
 * </ol>
 */
@Service
public class RedisOnlineRouteService implements OnlineRouteService, OnlineRouteHeartbeatWriter {

    private static final Logger logger = LoggerFactory.getLogger(RedisOnlineRouteService.class);

    private static final Duration DEFAULT_ROUTE_TTL = Duration.ofMinutes(30);

    /** HASH field 前缀：路由快照 JSON 字段 */
    private static final String ROUTE_PREFIX = "route:";

    /** HASH field 前缀：心跳时间戳字段 */
    private static final String HEARTBEAT_PREFIX = "heartbeat:";
    private static final String CONNECTION_PREFIX = "connection:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration routeTtl;
    private final LongSupplier currentTimeMillis;

    private final DefaultRedisScript<Long> registerScript;
    private final DefaultRedisScript<Long> refreshScript;
    private final DefaultRedisScript<Long> unregisterScript;
    private final DefaultRedisScript<Long> unregisterSessionScript;

    public RedisOnlineRouteService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this(redisTemplate, objectMapper, DEFAULT_ROUTE_TTL);
    }

    /**
     * 测试 / 自定义 TTL 入口。生产环境使用默认构造器即可。
     *
     * @param redisTemplate Redis 客户端，非空
     * @param objectMapper  JSON 序列化器，非空
     * @param routeTtl      路由 key TTL，非空
     */
    public RedisOnlineRouteService(StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   Duration routeTtl) {
        this(redisTemplate, objectMapper, routeTtl, System::currentTimeMillis);
    }

    RedisOnlineRouteService(StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper,
                            Duration routeTtl,
                            LongSupplier currentTimeMillis) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.routeTtl = Objects.requireNonNull(routeTtl, "routeTtl");
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
        this.registerScript = new DefaultRedisScript<>(registerLua(), Long.class);
        this.refreshScript = new DefaultRedisScript<>(refreshLua(), Long.class);
        this.unregisterScript = new DefaultRedisScript<>(unregisterLua(), Long.class);
        this.unregisterSessionScript = new DefaultRedisScript<>(unregisterSessionLua(), Long.class);
    }

    @Override
    public void register(RouteSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        String userId = snapshot.getUserId();
        String deviceId = snapshot.getDeviceId();
        if (userId == null || deviceId == null) {
            logger.warn("Register route skipped: userId or deviceId is null, userId={}, deviceId={}", userId, deviceId);
            return;
        }
        String routeJson;
        try {
            routeJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize RouteSnapshot: userId={}, deviceId={}", userId, deviceId, e);
            return;
        }
        Long heartbeatAt = snapshot.getHeartbeatAt() != null ? snapshot.getHeartbeatAt() : snapshot.getConnectedAt();
        if (heartbeatAt == null) {
            heartbeatAt = System.currentTimeMillis();
        }
        redisTemplate.execute(
                registerScript,
                Collections.singletonList(RedisKeys.onlineUser(userId)),
                deviceId,
                routeJson,
                String.valueOf(heartbeatAt),
                String.valueOf(routeTtl.toSeconds()),
                Objects.toString(snapshot.getConnectionId(), "")
        );
        registerSessionIndex(snapshot, routeJson);
    }

    @Override
    public void refresh(String userId, String deviceId, long heartbeatAt) {
        // 心跳为热路径，入参缺失直接静默返回，避免 NOOP 时打日志放大噪音；
        // register 是建立路由的关键事件，null 入参以 warn 提示，便于排查上游。
        if (userId == null || deviceId == null) {
            return;
        }
        // 脚本返回 1 表示命中已有 route 字段并刷新 heartbeat；返回 0 表示该设备未注册或路由已过期。
        // 当前未消费返回值（心跳丢失由后续 register/重连自愈），如需告警可在此检查返回值。
        redisTemplate.execute(
                refreshScript,
                Collections.singletonList(RedisKeys.onlineUser(userId)),
                deviceId,
                String.valueOf(heartbeatAt),
                String.valueOf(routeTtl.toSeconds())
        );
        refreshSessionIndex(userId, deviceId, heartbeatAt);
    }

    /**
     * 将同一调度周期的连接心跳用 pipeline 写入 Redis；每条 Lua 仍是单 key，兼容 Redis Cluster。
     */
    @Override
    public void refreshBatch(List<RouteHeartbeat> heartbeats) {
        if (heartbeats == null || heartbeats.isEmpty()) {
            return;
        }
        byte[] userScript = refreshGuardedLua().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] sessionScript = refreshSessionGuardedLua().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        List<RouteHeartbeat> valid = heartbeats.stream()
                .filter(heartbeat -> heartbeat != null
                        && heartbeat.userId() != null
                        && heartbeat.deviceId() != null
                        && heartbeat.connectionId() != null)
                .toList();
        List<Object> userResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (RouteHeartbeat heartbeat : valid) {
                connection.scriptingCommands().eval(
                        userScript,
                        ReturnType.INTEGER,
                        1,
                        bytes(RedisKeys.onlineUser(heartbeat.userId())),
                        bytes(heartbeat.deviceId()),
                        bytes(heartbeat.connectionId()),
                        bytes(Long.toString(heartbeat.heartbeatAt())),
                        bytes(Long.toString(routeTtl.toSeconds())));
            }
            return null;
        });
        List<RouteHeartbeat> accepted = new ArrayList<>();
        for (int i = 0; i < valid.size() && i < userResults.size(); i++) {
            Object result = userResults.get(i);
            RouteHeartbeat heartbeat = valid.get(i);
            if (result instanceof Number number && number.longValue() == 1L
                    && heartbeat.sessionId() != null && !heartbeat.sessionId().isBlank()) {
                accepted.add(heartbeat);
            }
        }
        if (accepted.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (RouteHeartbeat heartbeat : accepted) {
                String field = sessionField(heartbeat.userId(), heartbeat.deviceId());
                connection.scriptingCommands().eval(
                        sessionScript,
                        ReturnType.INTEGER,
                        1,
                        bytes(RedisKeys.onlineSession(heartbeat.sessionId())),
                        bytes(field),
                        bytes(heartbeat.connectionId()),
                        bytes(Long.toString(heartbeat.heartbeatAt())),
                        bytes(Long.toString(routeTtl.toSeconds())));
            }
            return null;
        });
    }

    @Override
    public void unregister(String userId, String deviceId, String connectionId) {
        // 同 refresh，热路径静默；unregister 对未存在的 hash 字段本就是 NOOP，无需区分。
        if (userId == null || deviceId == null) {
            return;
        }
        RouteSnapshot snapshot = findRouteByDevice(userId, deviceId);
        if (!matchesConnection(snapshot, connectionId)) {
            return;
        }
        Long removed = redisTemplate.execute(
                unregisterScript,
                Collections.singletonList(RedisKeys.onlineUser(userId)),
                deviceId,
                String.valueOf(routeTtl.toSeconds()),
                Objects.toString(connectionId, "")
        );
        if (Long.valueOf(1L).equals(removed)) {
            unregisterSessionIndex(snapshot, connectionId);
        }
    }

    @Override
    public List<RouteSnapshot> findByUser(String userId) {
        if (userId == null) {
            return List.of();
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RedisKeys.onlineUser(userId));
        if (entries == null || entries.isEmpty()) {
            ImMetrics.route("miss");
            return List.of();
        }
        List<RouteSnapshot> snapshots = new ArrayList<>(entries.size() / 2 + 1);
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String field = String.valueOf(entry.getKey());
            if (!field.startsWith(ROUTE_PREFIX)) {
                continue;
            }
            String routeJson = String.valueOf(entry.getValue());
            try {
                RouteSnapshot snapshot = objectMapper.readValue(routeJson, RouteSnapshot.class);
                // 合并心跳时间戳：如果是 HASH 中心跳字段存在则覆盖 snapshot.heartbeatAt
                Object heartbeatValue = entries.get(HEARTBEAT_PREFIX + snapshot.getDeviceId());
                if (heartbeatValue != null) {
                    long hb = Long.parseLong(String.valueOf(heartbeatValue));
                    snapshot.setHeartbeatAt(hb);
                }
                if (isStale(snapshot)) {
                    ImMetrics.route("stale");
                    removeStaleUserRoute(userId, snapshot);
                    continue;
                }
                snapshots.add(snapshot);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to deserialize RouteSnapshot: userId={}, field={}", userId, field, e);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse heartbeat timestamp: userId={}, field={}", userId, field, e);
            }
        }
        sortByDevice(snapshots);
        ImMetrics.route(snapshots.isEmpty() ? "miss" : "hit");
        return snapshots;
    }

    @Override
    public List<RouteSnapshot> findBySession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RedisKeys.onlineSession(sessionId));
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<RouteSnapshot> snapshots = new ArrayList<>(entries.size());
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String field = String.valueOf(entry.getKey());
            if (field.startsWith(HEARTBEAT_PREFIX) || field.startsWith(CONNECTION_PREFIX)) {
                continue;
            }
            try {
                RouteSnapshot snapshot = objectMapper.readValue(String.valueOf(entry.getValue()), RouteSnapshot.class);
                Object heartbeatValue = entries.get(HEARTBEAT_PREFIX + field);
                if (heartbeatValue != null) {
                    snapshot.setHeartbeatAt(Long.parseLong(String.valueOf(heartbeatValue)));
                }
                if (isStale(snapshot)) {
                    removeStaleUserRoute(snapshot.getUserId(), snapshot);
                    continue;
                }
                snapshots.add(snapshot);
            } catch (JsonProcessingException e) {
                logger.warn("Failed to deserialize session RouteSnapshot: sessionId={}, field={}",
                        sessionId, entry.getKey(), e);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse session heartbeat: sessionId={}, field={}",
                        sessionId, entry.getKey(), e);
            }
        }
        sortByDevice(snapshots);
        return snapshots;
    }

    /**
     * 注册：原子写入/覆盖 route:{deviceId} 和 heartbeat:{deviceId}，并刷新 key TTL。
     * 返回操作后该 key 上成对的 route 字段数量（即用户在线设备数）。
     */
    private String registerLua() {
        return """
                local key = KEYS[1]
                local deviceId = ARGV[1]
                local routeJson = ARGV[2]
                local heartbeatAt = ARGV[3]
                local ttlSeconds = tonumber(ARGV[4])
                redis.call('HSET', key, 'route:' .. deviceId, routeJson)
                redis.call('HSET', key, 'heartbeat:' .. deviceId, heartbeatAt)
                redis.call('HSET', key, 'connection:' .. deviceId, ARGV[5])
                redis.call('EXPIRE', key, ttlSeconds)
                return 1
                """;
    }

    /**
     * 心跳刷新：仅更新 heartbeat:{deviceId} 字段并续期 key TTL。
     * 如果 route:{deviceId} 字段不存在（设备未注册或已过期），返回 0，调用方可视为无效心跳，避免凭空创建孤儿心跳字段。
     */
    private String refreshLua() {
        return """
                local key = KEYS[1]
                local deviceId = ARGV[1]
                local heartbeatAt = ARGV[2]
                local ttlSeconds = tonumber(ARGV[3])
                if redis.call('HEXISTS', key, 'route:' .. deviceId) == 0 then
                    return 0
                end
                redis.call('HSET', key, 'heartbeat:' .. deviceId, heartbeatAt)
                redis.call('EXPIRE', key, ttlSeconds)
                return 1
                """;
    }

    private String refreshGuardedLua() {
        return """
                local key = KEYS[1]
                local deviceId = ARGV[1]
                if redis.call('HGET', key, 'connection:' .. deviceId) ~= ARGV[2] then return 0 end
                redis.call('HSET', key, 'heartbeat:' .. deviceId, ARGV[3])
                redis.call('EXPIRE', key, tonumber(ARGV[4]))
                return 1
                """;
    }

    private String refreshSessionGuardedLua() {
        return """
                local key = KEYS[1]
                local field = ARGV[1]
                if redis.call('HGET', key, 'connection:' .. field) ~= ARGV[2] then return 0 end
                redis.call('HSET', key, 'heartbeat:' .. field, ARGV[3])
                redis.call('EXPIRE', key, tonumber(ARGV[4]))
                return 1
                """;
    }

    /**
     * 注销：原子删除 route:{deviceId} 和 heartbeat:{deviceId}。
     * 如果删除后 HASH 已无任何字段，则 DEL 整个 key 以免留空 key 占内存；否则续期。
     * 返回剩余成对 route 字段数量。
     */
    private String unregisterLua() {
        return """
                local key = KEYS[1]
                local deviceId = ARGV[1]
                local ttlSeconds = tonumber(ARGV[2])
                local storedConnection = redis.call('HGET', key, 'connection:' .. deviceId)
                if storedConnection then
                    if storedConnection ~= ARGV[3] then return 0 end
                elseif ARGV[3] ~= '' then
                    return 0
                end
                redis.call('HDEL', key, 'route:' .. deviceId, 'heartbeat:' .. deviceId, 'connection:' .. deviceId)
                local remaining = redis.call('HLEN', key)
                if remaining == 0 then
                    redis.call('DEL', key)
                    return 0
                end
                redis.call('EXPIRE', key, ttlSeconds)
                return 1
                """;
    }

    private void registerSessionIndex(RouteSnapshot snapshot, String routeJson) {
        if (snapshot.getSessionId() == null || snapshot.getSessionId().isBlank()) {
            return;
        }
        String sessionKey = RedisKeys.onlineSession(snapshot.getSessionId());
        String field = sessionField(snapshot.getUserId(), snapshot.getDeviceId());
        redisTemplate.opsForHash().put(sessionKey, field, routeJson);
        redisTemplate.opsForHash().put(sessionKey, HEARTBEAT_PREFIX + field,
                Objects.toString(snapshot.getHeartbeatAt(), Objects.toString(snapshot.getConnectedAt(), "0")));
        redisTemplate.opsForHash().put(sessionKey, CONNECTION_PREFIX + field,
                Objects.toString(snapshot.getConnectionId(), ""));
        redisTemplate.expire(sessionKey, routeTtl);
    }

    private void refreshSessionIndex(String userId, String deviceId, long heartbeatAt) {
        RouteSnapshot snapshot = findRouteByDevice(userId, deviceId);
        if (snapshot == null || snapshot.getSessionId() == null || snapshot.getSessionId().isBlank()) {
            return;
        }
        snapshot.setHeartbeatAt(heartbeatAt);
        try {
            String sessionKey = RedisKeys.onlineSession(snapshot.getSessionId());
            redisTemplate.opsForHash().put(sessionKey,
                    sessionField(snapshot.getUserId(), snapshot.getDeviceId()),
                    objectMapper.writeValueAsString(snapshot));
            String field = sessionField(snapshot.getUserId(), snapshot.getDeviceId());
            redisTemplate.opsForHash().put(sessionKey, HEARTBEAT_PREFIX + field, Long.toString(heartbeatAt));
            redisTemplate.opsForHash().put(sessionKey, CONNECTION_PREFIX + field,
                    Objects.toString(snapshot.getConnectionId(), ""));
            redisTemplate.expire(sessionKey, routeTtl);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to refresh session route index: userId={}, deviceId={}", userId, deviceId, e);
        }
    }

    private String unregisterSessionLua() {
        return """
                local key = KEYS[1]
                local field = ARGV[1]
                local storedConnection = redis.call('HGET', key, 'connection:' .. field)
                if storedConnection then
                    if storedConnection ~= ARGV[2] then return 0 end
                elseif ARGV[2] ~= '' then
                    return 0
                end
                redis.call('HDEL', key, field, 'heartbeat:' .. field, 'connection:' .. field)
                if redis.call('HLEN', key) == 0 then redis.call('DEL', key) end
                return 1
                """;
    }

    private void unregisterSessionIndex(RouteSnapshot snapshot, String connectionId) {
        if (snapshot == null || snapshot.getSessionId() == null || snapshot.getSessionId().isBlank()) {
            return;
        }
        String sessionKey = RedisKeys.onlineSession(snapshot.getSessionId());
        String field = sessionField(snapshot.getUserId(), snapshot.getDeviceId());
        redisTemplate.execute(
                unregisterSessionScript,
                Collections.singletonList(sessionKey),
                field,
                Objects.toString(connectionId, ""));
    }

    private RouteSnapshot findRouteByDevice(String userId, String deviceId) {
        if (userId == null || deviceId == null) {
            return null;
        }
        Object routeJson = redisTemplate.opsForHash().get(RedisKeys.onlineUser(userId), ROUTE_PREFIX + deviceId);
        if (routeJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(String.valueOf(routeJson), RouteSnapshot.class);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to deserialize RouteSnapshot by device: userId={}, deviceId={}", userId, deviceId, e);
            return null;
        }
    }

    private boolean isStale(RouteSnapshot snapshot) {
        Long heartbeatAt = snapshot.getHeartbeatAt() != null
                ? snapshot.getHeartbeatAt()
                : snapshot.getConnectedAt();
        return heartbeatAt == null
                || heartbeatAt < currentTimeMillis.getAsLong() - routeTtl.toMillis();
    }

    private void removeStaleUserRoute(String userId, RouteSnapshot snapshot) {
        if (userId == null || snapshot.getDeviceId() == null) {
            return;
        }
        unregister(userId, snapshot.getDeviceId(), snapshot.getConnectionId());
    }

    private static void sortByDevice(List<RouteSnapshot> snapshots) {
        snapshots.sort(Comparator.comparing(RouteSnapshot::getDeviceId, Comparator.nullsLast(String::compareTo)));
    }

    private static String sessionField(String userId, String deviceId) {
        return Objects.toString(userId, "") + ":" + Objects.toString(deviceId, "");
    }

    private static boolean matchesConnection(RouteSnapshot snapshot, String connectionId) {
        if (snapshot == null) {
            return false;
        }
        if (connectionId == null || connectionId.isBlank()) {
            return true;
        }
        return connectionId.equals(snapshot.getConnectionId());
    }

    private byte[] bytes(String value) {
        return redisTemplate.getStringSerializer().serialize(value);
    }
}
