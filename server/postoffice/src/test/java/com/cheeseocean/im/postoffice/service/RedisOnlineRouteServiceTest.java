package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.core.cache.MultiLevelCacheService;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisOnlineRouteServiceTest {

    @Test
    void registerShouldStoreUserDeviceAndGatewayNode() {
        MultiLevelCacheService cacheService = mock(MultiLevelCacheService.class);
        when(cacheService.getOrLoad(eq(RedisKeys.onlineUser("u1")), eq(List.class), eq(Duration.ofMinutes(30)), any()))
                .thenReturn(List.of());

        RedisOnlineRouteService service = new RedisOnlineRouteService(cacheService);

        RouteSnapshot snapshot = new RouteSnapshot();
        snapshot.setUserId("u1");
        snapshot.setDeviceId("ios-1");
        snapshot.setGatewayNode("gateway-a");
        snapshot.setConnectedAt(100L);
        snapshot.setHeartbeatAt(200L);

        service.register(snapshot);

        verify(cacheService).put(eq(RedisKeys.onlineUser("u1")), any(List.class), eq(Duration.ofMinutes(30)));
    }

    @Test
    void findByUserShouldRestoreStoredSnapshot() {
        MultiLevelCacheService cacheService = mock(MultiLevelCacheService.class);
        RouteSnapshot stored = new RouteSnapshot();
        stored.setUserId("u1");
        stored.setDeviceId("ios-1");
        stored.setGatewayNode("gateway-a");
        stored.setConnectedAt(100L);
        stored.setHeartbeatAt(200L);
        when(cacheService.getOrLoad(eq(RedisKeys.onlineUser("u1")), eq(List.class), eq(Duration.ofMinutes(30)), any()))
                .thenReturn(List.of(stored));

        RedisOnlineRouteService service = new RedisOnlineRouteService(cacheService);

        RouteSnapshot snapshot = service.findByUser("u1").get(0);

        assertNotNull(snapshot);
        assertEquals("u1", snapshot.getUserId());
        assertEquals("ios-1", snapshot.getDeviceId());
        assertEquals("gateway-a", snapshot.getGatewayNode());
        assertEquals(100L, snapshot.getConnectedAt());
        assertEquals(200L, snapshot.getHeartbeatAt());
    }

    @Test
    void unregisterShouldDeleteStoredDeviceFields() {
        MultiLevelCacheService cacheService = mock(MultiLevelCacheService.class);
        RouteSnapshot stored = new RouteSnapshot();
        stored.setUserId("u1");
        stored.setDeviceId("ios-1");
        stored.setGatewayNode("gateway-a");
        when(cacheService.getOrLoad(eq(RedisKeys.onlineUser("u1")), eq(List.class), eq(Duration.ofMinutes(30)), any()))
                .thenReturn(List.of(stored));

        RedisOnlineRouteService service = new RedisOnlineRouteService(cacheService);

        service.unregister("u1", "ios-1");

        verify(cacheService).put(eq(RedisKeys.onlineUser("u1")), eq(List.of()), eq(Duration.ofMinutes(30)));
    }
}
