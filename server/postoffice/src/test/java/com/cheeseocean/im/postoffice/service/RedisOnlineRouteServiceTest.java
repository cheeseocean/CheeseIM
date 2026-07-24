package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.api.dto.route.RouteSnapshot;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单元测试覆盖 {@link RedisOnlineRouteService} 的 register/refresh/unregister/findByUser 行为。
 *
 * <p>策略：通过 Mockito 模拟 {@link StringRedisTemplate} 与 {@link HashOperations}，
 * 验证 (a) 写路径调用 Lua 脚本时传入正确的 key/argv（保证原子性走脚本、而非 read-modify-write），
 * (b) 读路径能够正确解析 HASH 中 route:* / heartbeat:* 双字段并合并心跳时间戳。
 * 真实 Redis 上的 Lua 反序列化端到端由集成测试覆盖。
 */
class RedisOnlineRouteServiceTest {

    private static final Duration TTL = Duration.ofMinutes(30);

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private RedisOnlineRouteService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        service = new RedisOnlineRouteService(redisTemplate, new ObjectMapper(), TTL, () -> 1_000_000L);
    }

    @Test
    void registerShouldExecuteLuaScriptWithRouteAndHeartbeatArgs() {
        RouteSnapshot snapshot = new RouteSnapshot();
        snapshot.setUserId("u1");
        snapshot.setDeviceId("ios-1");
        snapshot.setGatewayNode("gateway-a");
        snapshot.setConnectedAt(100L);
        snapshot.setHeartbeatAt(200L);

        service.register(snapshot);

        // 验证写路径必走 Lua，ARGV 同时保存 connectionId，供迟到心跳/注销做原子身份校验。
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisKeys.onlineUser("u1"))),
                eq("ios-1"),
                any(String.class),
                eq("200"),
                eq(String.valueOf(TTL.toSeconds())),
                eq(""));
    }

    @Test
    void registerShouldFallbackToConnectedAtWhenHeartbeatMissing() {
        RouteSnapshot snapshot = new RouteSnapshot();
        snapshot.setUserId("u1");
        snapshot.setDeviceId("ios-1");
        snapshot.setGatewayNode("gateway-a");
        snapshot.setConnectedAt(100L);

        service.register(snapshot);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                anyList(),
                eq("ios-1"),
                any(String.class),
                eq("100"),
                eq(String.valueOf(TTL.toSeconds())),
                eq(""));
    }

    @Test
    void registerShouldSkipWhenUserOrDeviceIdNull() {
        RouteSnapshot snapshot = new RouteSnapshot();
        snapshot.setUserId(null);
        snapshot.setDeviceId("ios-1");

        service.register(snapshot);

        // 校验既没有调用 Lua 脚本，也没有访问 Redis HASH（提前 return 应跳过所有 IO）
        verify(redisTemplate, org.mockito.Mockito.never())
                .execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any());
        org.mockito.Mockito.verifyNoInteractions(hashOps);
    }

    @Test
    void refreshShouldExecuteLuaScriptWithDeviceAndHeartbeat() {
        service.refresh("u1", "ios-1", 99L);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisKeys.onlineUser("u1"))),
                eq("ios-1"),
                eq("99"),
                eq(String.valueOf(TTL.toSeconds())));
    }

    @Test
    void unregisterShouldExecuteLuaScriptToDeleteRouteAndHeartbeat() throws Exception {
        RouteSnapshot stored = new RouteSnapshot();
        stored.setUserId("u1");
        stored.setConnectionId("conn-1");
        stored.setDeviceId("ios-1");
        when(hashOps.get(RedisKeys.onlineUser("u1"), "route:ios-1"))
                .thenReturn(new ObjectMapper().writeValueAsString(stored));

        service.unregister("u1", "ios-1", "conn-1");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisKeys.onlineUser("u1"))),
                eq("ios-1"),
                eq(String.valueOf(TTL.toSeconds())),
                eq("conn-1"));
    }

    @Test
    void findByUserShouldMergeRouteJsonAndHeartbeatField() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RouteSnapshot stored = new RouteSnapshot();
        stored.setUserId("u1");
        stored.setDeviceId("ios-1");
        stored.setGatewayNode("gateway-a");
        stored.setConnectedAt(100L);
        stored.setHeartbeatAt(200L);

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("route:ios-1", mapper.writeValueAsString(stored));
        entries.put("heartbeat:ios-1", "777");
        when(hashOps.entries(RedisKeys.onlineUser("u1"))).thenReturn(entries);

        List<RouteSnapshot> result = service.findByUser("u1");

        assertEquals(1, result.size());
        RouteSnapshot snapshot = result.get(0);
        assertNotNull(snapshot);
        assertEquals("u1", snapshot.getUserId());
        assertEquals("ios-1", snapshot.getDeviceId());
        assertEquals("gateway-a", snapshot.getGatewayNode());
        assertEquals(100L, snapshot.getConnectedAt());
        // heartbeat 应被 HASH 中的心跳字段覆盖，证明心跳字段被合并
        assertEquals(777L, snapshot.getHeartbeatAt());
    }

    @Test
    void findByUserShouldReturnEmptyWhenKeyMissing() {
        when(hashOps.entries(RedisKeys.onlineUser("u1"))).thenReturn(new HashMap<>());

        assertTrue(service.findByUser("u1").isEmpty());
    }

    @Test
    void findByUserShouldSortByDeviceId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RouteSnapshot first = new RouteSnapshot();
        first.setUserId("u1");
        first.setDeviceId("zzz-1");
        first.setHeartbeatAt(999_999L);
        RouteSnapshot second = new RouteSnapshot();
        second.setUserId("u1");
        second.setDeviceId("aaa-1");
        second.setHeartbeatAt(999_999L);

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("route:zzz-1", mapper.writeValueAsString(first));
        entries.put("route:aaa-1", mapper.writeValueAsString(second));
        when(hashOps.entries(RedisKeys.onlineUser("u1"))).thenReturn(entries);

        List<RouteSnapshot> result = service.findByUser("u1");

        assertEquals(2, result.size());
        assertEquals("aaa-1", result.get(0).getDeviceId());
        assertEquals("zzz-1", result.get(1).getDeviceId());
    }

    @Test
    void findBySessionShouldReturnAllRouteSnapshots() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RouteSnapshot first = new RouteSnapshot();
        first.setUserId("u1");
        first.setSessionId("sess-1");
        first.setDeviceId("ios-1");
        first.setGatewayNode("node-a");
        first.setHeartbeatAt(999_999L);
        RouteSnapshot second = new RouteSnapshot();
        second.setUserId("u1");
        second.setSessionId("sess-1");
        second.setDeviceId("android-1");
        second.setGatewayNode("node-b");
        second.setHeartbeatAt(999_999L);

        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("u1:ios-1", mapper.writeValueAsString(first));
        entries.put("u1:android-1", mapper.writeValueAsString(second));
        when(hashOps.entries(RedisKeys.onlineSession("sess-1"))).thenReturn(entries);

        List<RouteSnapshot> result = service.findBySession("sess-1");

        assertEquals(2, result.size());
        assertEquals("android-1", result.get(0).getDeviceId());
        assertEquals("node-b", result.get(0).getGatewayNode());
        assertEquals("ios-1", result.get(1).getDeviceId());
        assertEquals("node-a", result.get(1).getGatewayNode());
    }

    @Test
    void findByUserShouldTolerateMalformedRouteEntry() {
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("route:ios-1", "{not json");
        entries.put("heartbeat:ios-1", "777");
        when(hashOps.entries(RedisKeys.onlineUser("u1"))).thenReturn(entries);

        // 解析失败不应抛异常，仅返回空
        assertTrue(service.findByUser("u1").isEmpty());
    }

    @Test
    void findByUserShouldFilterAndCleanOnlyStaleDeviceRoute() throws Exception {
        service = new RedisOnlineRouteService(redisTemplate, new ObjectMapper(), TTL,
                () -> TTL.toMillis() + 1_000L);
        ObjectMapper mapper = new ObjectMapper();
        RouteSnapshot stale = route("u1", "old-device", 1L);
        stale.setSessionId("session-old");
        RouteSnapshot active = route("u1", "active-device", TTL.toMillis() + 999L);
        Map<Object, Object> entries = new LinkedHashMap<>();
        entries.put("route:old-device", mapper.writeValueAsString(stale));
        entries.put("heartbeat:old-device", "1");
        entries.put("route:active-device", mapper.writeValueAsString(active));
        entries.put("heartbeat:active-device", String.valueOf(TTL.toMillis() + 999L));
        when(hashOps.entries(RedisKeys.onlineUser("u1"))).thenReturn(entries);
        when(hashOps.get(RedisKeys.onlineUser("u1"), "route:old-device"))
                .thenReturn(mapper.writeValueAsString(stale));
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(RedisKeys.onlineUser("u1"))),
                eq("old-device"),
                eq(String.valueOf(TTL.toSeconds())),
                eq(""))).thenReturn(1L);

        List<RouteSnapshot> result = service.findByUser("u1");

        assertEquals(List.of("active-device"), result.stream().map(RouteSnapshot::getDeviceId).toList());
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisKeys.onlineUser("u1"))),
                eq("old-device"),
                eq(String.valueOf(TTL.toSeconds())),
                eq(""));
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisKeys.onlineSession("session-old"))),
                eq("u1:old-device"),
                eq(""));
        verify(hashOps, never()).delete(
                RedisKeys.onlineUser("u1"), "route:active-device", "heartbeat:active-device");
    }

    private static RouteSnapshot route(String userId, String deviceId, long heartbeatAt) {
        RouteSnapshot snapshot = new RouteSnapshot();
        snapshot.setUserId(userId);
        snapshot.setDeviceId(deviceId);
        snapshot.setHeartbeatAt(heartbeatAt);
        return snapshot;
    }
}
