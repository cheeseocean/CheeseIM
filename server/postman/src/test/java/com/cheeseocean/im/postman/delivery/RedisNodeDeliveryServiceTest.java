package com.cheeseocean.im.postman.delivery;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.route.NodeQueueMessage;
import com.cheeseocean.im.common.api.enums.NodeQueueMessageType;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.queue.NodeQueueRedisScripts;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单元测试覆盖 {@link RedisNodeDeliveryService#deliver} 的关键行为：
 * <ul>
 *   <li>正常路径：LPUSH NodeQueueMessage envelope 到 delivery:node:{gatewayNode}</li>
 *   <li>gatewayNode 为空/null → 返回 false</li>
 *   <li>req 为 null 或 userId 为 null → 返回 false</li>
 *   <li>Redis 异常 → 返回 false（不抛异常）</li>
 * </ul>
 */
class RedisNodeDeliveryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void deliverShouldPublishConsumerCompatibleEnvelope() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);

        ObjectMapper objectMapper = new ObjectMapper();
        RedisNodeDeliveryService service = new RedisNodeDeliveryService(redisTemplate, objectMapper);

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userA");
        boolean result = service.deliver("node-1", req);

        assertTrue(result);
        verify(redisTemplate).execute(eq(NodeQueueRedisScripts.ENQUEUE),
                eq(java.util.List.of(RedisKeys.deliveryNodeQueue("node-1"))), any(Object[].class));

        NodeQueueMessage envelope = objectMapper.readValue(service.serializeDelivery(req), NodeQueueMessage.class);
        assertTrue(NodeQueueMessageType.fromCode(envelope.getType()) == NodeQueueMessageType.DELIVERY);
        DispatchMessageReq decoded = objectMapper.readValue(envelope.getPayload(), DispatchMessageReq.class);
        assertTrue("userA".equals(decoded.getUserId()));
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
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenThrow(new RuntimeException("Redis down"));

        RedisNodeDeliveryService service = new RedisNodeDeliveryService(redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper());

        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userA");
        // 异常被捕获，返回 false，不向上抛
        assertFalse(service.deliver("node-1", req));
    }
}
