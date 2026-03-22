package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisOnlineRouteServiceTest {

    @Test
    void registerShouldStoreUserDeviceAndGatewayNode() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        RedisOnlineRouteService service = new RedisOnlineRouteService(redisTemplate);

        RouteSnapshot snapshot = new RouteSnapshot();
        snapshot.setUserId("u1");
        snapshot.setDeviceId("ios-1");
        snapshot.setGatewayNode("gateway-a");
        snapshot.setConnectedAt(100L);
        snapshot.setHeartbeatAt(200L);

        service.register(snapshot);

        verify(hashOperations).putAll(eq(RedisKeys.onlineUser("u1")), any(Map.class));
        verify(redisTemplate).expire(RedisKeys.onlineUser("u1"), 30, TimeUnit.MINUTES);
    }

    @Test
    void findByUserShouldRestoreStoredSnapshot() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(RedisKeys.onlineUser("u1"))).thenReturn(Map.of(
                "ios-1.gatewayNode", "gateway-a",
                "ios-1.connectedAt", 100L,
                "ios-1.heartbeatAt", 200L
        ));

        RedisOnlineRouteService service = new RedisOnlineRouteService(redisTemplate);

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
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        RedisOnlineRouteService service = new RedisOnlineRouteService(redisTemplate);

        service.unregister("u1", "ios-1");

        verify(hashOperations).delete(RedisKeys.onlineUser("u1"),
                "ios-1.gatewayNode", "ios-1.connectedAt", "ios-1.heartbeatAt");
    }
}
