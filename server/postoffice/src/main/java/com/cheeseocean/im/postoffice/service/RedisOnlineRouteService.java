package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.constants.RedisKeys;
import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RedisOnlineRouteService implements OnlineRouteService {

    private static final long ROUTE_TTL_MINUTES = 30;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisOnlineRouteService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void register(RouteSnapshot snapshot) {
        String key = RedisKeys.onlineRoute(snapshot.getUserId());
        redisTemplate.opsForHash().putAll(key, Map.of(
                field(snapshot.getDeviceId(), "gatewayNode"), snapshot.getGatewayNode(),
                field(snapshot.getDeviceId(), "connectedAt"), snapshot.getConnectedAt(),
                field(snapshot.getDeviceId(), "heartbeatAt"), snapshot.getHeartbeatAt()
        ));
        redisTemplate.expire(key, ROUTE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void refresh(String userId, String deviceId, long heartbeatAt) {
        String key = RedisKeys.onlineRoute(userId);
        redisTemplate.opsForHash().put(key, field(deviceId, "heartbeatAt"), heartbeatAt);
        redisTemplate.expire(key, ROUTE_TTL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void unregister(String userId, String deviceId) {
        redisTemplate.opsForHash().delete(RedisKeys.onlineRoute(userId),
                field(deviceId, "gatewayNode"),
                field(deviceId, "connectedAt"),
                field(deviceId, "heartbeatAt"));
    }

    @Override
    public List<RouteSnapshot> findByUser(String userId) {
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        Map<Object, Object> entries = hashOperations.entries(RedisKeys.onlineRoute(userId));
        Map<String, RouteSnapshot> snapshots = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String field = String.valueOf(entry.getKey());
            String[] parts = field.split("\\.", 2);
            if (parts.length != 2) {
                continue;
            }
            RouteSnapshot snapshot = snapshots.computeIfAbsent(parts[0], deviceId -> {
                RouteSnapshot item = new RouteSnapshot();
                item.setUserId(userId);
                item.setDeviceId(deviceId);
                return item;
            });
            apply(snapshot, parts[1], entry.getValue());
        }
        return new ArrayList<>(snapshots.values());
    }

    private static String field(String deviceId, String suffix) {
        return deviceId + "." + suffix;
    }

    private static void apply(RouteSnapshot snapshot, String property, Object value) {
        if ("gatewayNode".equals(property)) {
            snapshot.setGatewayNode(String.valueOf(value));
        } else if ("connectedAt".equals(property)) {
            snapshot.setConnectedAt(asLong(value));
        } else if ("heartbeatAt".equals(property)) {
            snapshot.setHeartbeatAt(asLong(value));
        }
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }
}
