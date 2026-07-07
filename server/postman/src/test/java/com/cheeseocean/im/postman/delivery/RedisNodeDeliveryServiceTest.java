package com.cheeseocean.im.postman.delivery;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单元测试覆盖 {@link RedisNodeDeliveryService#deliver} 的关键行为：
 * <ul>
 *   <li>正常路径：LPUSH DispatchMessageReq JSON 到 delivery:node:{gatewayNode}</li>
 *   <li>gatewayNode 为空/null → 返回 false</li>
 *   <li>req 为 null 或 userId 为 null → 返回 false</li>
 *   <li>Redis 异常 → 返回 false（不抛异常）</li>
 * </ul>
 */
class RedisNodeDeliveryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void deliverShouldLeftPushJsonToNodeQueue() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.leftPush(anyString(), anyString())).thenReturn(1L);

        RedisNodeDeliveryService service = new RedisNodeDeliveryService(redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper());

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userA");
        boolean result = service.deliver("node-1", req);

        assertTrue(result);
        verify(listOps).leftPush(eq(RedisKeys.deliveryNodeQueue("node-1")), anyString());
    }

    @Test
    void deliverShouldReturnFalseWhenGatewayNodeIsNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisNodeDeliveryService service = new RedisNodeDeliveryService(redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper());

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userA");
        assertFalse(service.deliver(null, req));
        assertFalse(service.deliver("", req));
        assertFalse(service.deliver("  ", req));
    }

    @Test
    void deliverShouldReturnFalseWhenReqOrUserIdIsNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisNodeDeliveryService service = new RedisNodeDeliveryService(redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper());

        assertFalse(service.deliver("node-1", null));

        DispatchMessageReq req = new DispatchMessageReq(); // userId=null
        assertFalse(service.deliver("node-1", req));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deliverShouldReturnFalseOnRedisException() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.leftPush(anyString(), anyString())).thenThrow(new RuntimeException("Redis down"));

        RedisNodeDeliveryService service = new RedisNodeDeliveryService(redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper());

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userA");
        // 异常被捕获，返回 false，不向上抛
        assertFalse(service.deliver("node-1", req));
    }
}
