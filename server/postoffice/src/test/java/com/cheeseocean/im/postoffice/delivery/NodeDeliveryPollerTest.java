package com.cheeseocean.im.postoffice.delivery;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.dto.route.NodeQueueMessage;
import com.cheeseocean.im.common.api.enums.NodeQueueMessageType;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.queue.NodeQueueRedisScripts;
import com.cheeseocean.im.postoffice.api.OnlineDispatcherImpl;
import com.cheeseocean.im.postoffice.config.NodeIdentityProvider;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单元测试覆盖 {@link NodeDeliveryPoller} 的消费逻辑：
 * <ul>
 *   <li>BRPOPLPUSH 返回 envelope → 本地投递成功后 ACK processing</li>
 *   <li>本地投递失败 → 从 processing 移除并放回 ready 重试</li>
 *   <li>非法 JSON → 从 processing 移入死信队列</li>
 *   <li>shutdown → 线程退出</li>
 * </ul>
 */
class NodeDeliveryPollerTest {

    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private ListOperations<String, String> listOps;
    private ObjectMapper objectMapper;
    private OnlineDispatcherImpl onlineDispatcher;
    private ConnectionManager connectionManager;
    private NodeIdentityProvider nodeIdentityProvider;
    private NodeDeliveryPoller poller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOps = mock(ListOperations.class);
        objectMapper = new ObjectMapper();
        onlineDispatcher = mock(OnlineDispatcherImpl.class);
        connectionManager = mock(ConnectionManager.class);
        nodeIdentityProvider = mock(NodeIdentityProvider.class);

        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.RECOVER_EXPIRED), anyList(), any(Object[].class)))
                .thenReturn(0L);
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.ACK), anyList(), any(Object[].class))).thenReturn(1L);
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.COMPLETE), anyList(), any(Object[].class))).thenReturn(1L);
        when(nodeIdentityProvider.getNodeId()).thenReturn("test-node-1");
    }

    @Test
    void pollLoopShouldDispatchWhenJsonReceived() throws Exception {
        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");

        String json = objectMapper.writeValueAsString(NodeQueueMessage.of(
                NodeQueueMessageType.DELIVERY, objectMapper.writeValueAsString(req)));
        String ready = RedisKeys.deliveryNodeQueue("test-node-1");
        String processing = RedisKeys.deliveryNodeProcessingQueue("test-node-1");
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.CLAIM),
                eq(List.of(ready, processing, processing + ":leases")), any(Object[].class)))
                .thenReturn(json)
                .thenAnswer(invocation -> { poller.stop(); return null; });

        DispatchMessageResp resp = new DispatchMessageResp();
        resp.setResults(List.of(new DispatchResult("conn-1", true, "OK", "delivered")));
        when(onlineDispatcher.dispatchMessage(any(DispatchMessageReq.class))).thenReturn(resp);

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, connectionManager,
                nodeIdentityProvider);
        poller.start();

        verify(onlineDispatcher, timeout(3000)).dispatchMessage(any(DispatchMessageReq.class));
        verify(redisTemplate, timeout(3000)).execute(eq(NodeQueueRedisScripts.ACK),
                eq(List.of(processing, processing + ":leases")), any(Object[].class));
    }

    @Test
    void pollLoopShouldNotDispatchWhenTimeout() throws Exception {
        String ready = RedisKeys.deliveryNodeQueue("test-node-1");
        String processing = RedisKeys.deliveryNodeProcessingQueue("test-node-1");
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.CLAIM), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    // timeout → return null, then stop
                    poller.stop();
                    return null;
                });

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, connectionManager,
                nodeIdentityProvider);
        poller.start();

        poller.stop();

        verify(onlineDispatcher, never()).dispatchMessage(any(DispatchMessageReq.class));
    }

    @Test
    void malformedJsonShouldMoveToDeadLetterQueue() {
        String ready = RedisKeys.deliveryNodeQueue("test-node-1");
        String processing = RedisKeys.deliveryNodeProcessingQueue("test-node-1");
        String dead = RedisKeys.deliveryNodeDeadLetterQueue("test-node-1");
        String invalid = "{invalid json";
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.CLAIM), anyList(), any(Object[].class)))
                .thenReturn(invalid)
                .thenAnswer(invocation -> { poller.stop(); return null; });

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, connectionManager,
                nodeIdentityProvider);
        poller.start();

        verify(redisTemplate, timeout(3000)).execute(eq(NodeQueueRedisScripts.COMPLETE),
                eq(List.of(processing, processing + ":leases", dead)), any(Object[].class));
        verify(onlineDispatcher, never()).dispatchMessage(any(DispatchMessageReq.class));
    }

    @Test
    void failedDispatchShouldRequeueWithIncrementedRetryCount() throws Exception {
        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");
        String json = objectMapper.writeValueAsString(NodeQueueMessage.of(
                NodeQueueMessageType.DELIVERY, objectMapper.writeValueAsString(req)));
        String ready = RedisKeys.deliveryNodeQueue("test-node-1");
        String processing = RedisKeys.deliveryNodeProcessingQueue("test-node-1");
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.CLAIM), anyList(), any(Object[].class)))
                .thenReturn(json)
                .thenAnswer(invocation -> { poller.stop(); return null; });
        when(onlineDispatcher.dispatchMessage(any())).thenThrow(new IllegalStateException("channel failed"));

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, connectionManager,
                nodeIdentityProvider);
        poller.start();

        verify(redisTemplate, timeout(3000)).execute(eq(NodeQueueRedisScripts.COMPLETE),
                eq(List.of(processing, processing + ":leases", ready)), any(Object[].class));
    }

    @Test
    void recoveryFailureShouldNotTerminatePoller() throws Exception {
        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("user-after-redis-recovery");
        String json = objectMapper.writeValueAsString(NodeQueueMessage.of(
                NodeQueueMessageType.DELIVERY, objectMapper.writeValueAsString(req)));
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.RECOVER_EXPIRED), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"))
                .thenReturn(0L);
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.CLAIM), anyList(), any(Object[].class)))
                .thenReturn(json)
                .thenAnswer(invocation -> { poller.stop(); return null; });
        DispatchMessageResp response = new DispatchMessageResp();
        response.setResults(List.of(new DispatchResult("conn-1", true, "OK", "delivered")));
        when(onlineDispatcher.dispatchMessage(any())).thenReturn(response);

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, connectionManager,
                nodeIdentityProvider);
        poller.start();

        verify(onlineDispatcher, timeout(3000)).dispatchMessage(any());
    }

    @Test
    void infrastructureMoveFailureShouldNotBeMisclassifiedAsMalformed() throws Exception {
        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");
        String json = objectMapper.writeValueAsString(NodeQueueMessage.of(
                NodeQueueMessageType.DELIVERY, objectMapper.writeValueAsString(req)));
        String ready = RedisKeys.deliveryNodeQueue("test-node-1");
        String processing = RedisKeys.deliveryNodeProcessingQueue("test-node-1");
        String dead = RedisKeys.deliveryNodeDeadLetterQueue("test-node-1");
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.CLAIM), anyList(), any(Object[].class)))
                .thenReturn(json)
                .thenAnswer(invocation -> { poller.stop(); return null; });
        when(onlineDispatcher.dispatchMessage(any())).thenThrow(new IllegalStateException("channel failed"));
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.COMPLETE),
                eq(List.of(processing, processing + ":leases", ready)), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, connectionManager,
                nodeIdentityProvider);
        poller.start();

        verify(redisTemplate, timeout(3000)).execute(eq(NodeQueueRedisScripts.COMPLETE),
                eq(List.of(processing, processing + ":leases", ready)), any(Object[].class));
        verify(redisTemplate, never()).execute(eq(NodeQueueRedisScripts.COMPLETE),
                eq(List.of(processing, processing + ":leases", dead)), any(Object[].class));
    }

    @Test
    void readyOverflowShouldRetainClaimInsteadOfSendingToDeadLetter() throws Exception {
        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("user-overflow");
        String json = objectMapper.writeValueAsString(NodeQueueMessage.of(
                NodeQueueMessageType.DELIVERY, objectMapper.writeValueAsString(req)));
        String ready = RedisKeys.deliveryNodeQueue("test-node-1");
        String processing = RedisKeys.deliveryNodeProcessingQueue("test-node-1");
        String dead = RedisKeys.deliveryNodeDeadLetterQueue("test-node-1");
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.CLAIM), anyList(), any(Object[].class)))
                .thenReturn(json)
                .thenAnswer(invocation -> { poller.stop(); return null; });
        when(onlineDispatcher.dispatchMessage(any())).thenThrow(new IllegalStateException("channel failed"));
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.COMPLETE),
                eq(List.of(processing, processing + ":leases", ready)), any(Object[].class)))
                .thenReturn(-1L);

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, connectionManager,
                nodeIdentityProvider);
        poller.start();

        verify(redisTemplate, timeout(3000)).execute(eq(NodeQueueRedisScripts.COMPLETE),
                eq(List.of(processing, processing + ":leases", ready)), any(Object[].class));
        verify(redisTemplate, never()).execute(eq(NodeQueueRedisScripts.COMPLETE),
                eq(List.of(processing, processing + ":leases", dead)), any(Object[].class));
    }

    @Test
    void shouldRecoverExpiredClaimBeforeClaimingNextMessage() {
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.RECOVER_EXPIRED), anyList(), any(Object[].class)))
                .thenReturn(1L, 0L);
        when(redisTemplate.execute(eq(NodeQueueRedisScripts.CLAIM), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> { poller.stop(); return null; });

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, connectionManager,
                nodeIdentityProvider);
        poller.start();

        verify(redisTemplate, timeout(3000).times(2)).execute(eq(NodeQueueRedisScripts.RECOVER_EXPIRED),
                eq(List.of(RedisKeys.deliveryNodeProcessingQueue("test-node-1"),
                        RedisKeys.deliveryNodeProcessingQueue("test-node-1") + ":leases",
                        RedisKeys.deliveryNodeQueue("test-node-1"))), any(Object[].class));
    }
}
