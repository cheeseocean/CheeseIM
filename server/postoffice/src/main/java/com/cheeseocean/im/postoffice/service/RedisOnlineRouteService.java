package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.core.cache.MultiLevelCacheService;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.Duration;

@Service
public class RedisOnlineRouteService implements OnlineRouteService {

    private static final Duration ROUTE_TTL = Duration.ofMinutes(30);

    private final MultiLevelCacheService cacheService;

    public RedisOnlineRouteService(MultiLevelCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void register(RouteSnapshot snapshot) {
        String key = RedisKeys.onlineUser(snapshot.getUserId());
        List<RouteSnapshot> snapshots = new ArrayList<>(findByUser(snapshot.getUserId()));
        snapshots.removeIf(existing -> sameDevice(existing, snapshot.getDeviceId()));
        snapshots.add(copyOf(snapshot));
        sortByDevice(snapshots);
        cacheService.put(key, snapshots, ROUTE_TTL);
    }

    @Override
    public void refresh(String userId, String deviceId, long heartbeatAt) {
        String key = RedisKeys.onlineUser(userId);
        List<RouteSnapshot> snapshots = new ArrayList<>(findByUser(userId));
        for (RouteSnapshot snapshot : snapshots) {
            if (sameDevice(snapshot, deviceId)) {
                snapshot.setHeartbeatAt(heartbeatAt);
            }
        }
        cacheService.put(key, snapshots, ROUTE_TTL);
    }

    @Override
    public void unregister(String userId, String deviceId) {
        String key = RedisKeys.onlineUser(userId);
        List<RouteSnapshot> snapshots = new ArrayList<>(findByUser(userId));
        snapshots.removeIf(snapshot -> sameDevice(snapshot, deviceId));
        cacheService.put(key, snapshots, ROUTE_TTL);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RouteSnapshot> findByUser(String userId) {
        List<?> stored = cacheService.getOrLoad(RedisKeys.onlineUser(userId), List.class, ROUTE_TTL, List::of);
        List<RouteSnapshot> snapshots = new ArrayList<>();
        for (Object item : stored) {
            if (item instanceof RouteSnapshot routeSnapshot) {
                snapshots.add(copyOf(routeSnapshot));
            }
        }
        sortByDevice(snapshots);
        return snapshots;
    }

    private static boolean sameDevice(RouteSnapshot snapshot, String deviceId) {
        return snapshot != null && snapshot.getDeviceId() != null && snapshot.getDeviceId().equals(deviceId);
    }

    private static RouteSnapshot copyOf(RouteSnapshot source) {
        RouteSnapshot snapshot = new RouteSnapshot();
        snapshot.setUserId(source.getUserId());
        snapshot.setDeviceId(source.getDeviceId());
        snapshot.setGatewayNode(source.getGatewayNode());
        snapshot.setConnectedAt(source.getConnectedAt());
        snapshot.setHeartbeatAt(source.getHeartbeatAt());
        return snapshot;
    }

    private static void sortByDevice(List<RouteSnapshot> snapshots) {
        snapshots.sort(Comparator.comparing(RouteSnapshot::getDeviceId, Comparator.nullsLast(String::compareTo)));
    }
}
