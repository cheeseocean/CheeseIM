package com.cheeseocean.im.postoffice.delivery;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.postoffice.api.OnlineDispatcherImpl;
import com.cheeseocean.im.postoffice.config.NodeIdentityProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单元测试覆盖 {@link NodeDeliveryPoller} 的消费逻辑：
 * <ul>
 *   <li>BRPOP 返回 JSON → 反序列化并调用 OnlineDispatcherImpl.dispatchMessage</li>
 *   <li>BRPOP 返回 null（超时）→ 不调用 dispatch，循环继续</li>
 *   <li>BRPOP 返回非法 JSON → 异常被吞掉，循环继续</li>
 *   <li>shutdown → 线程退出</li>
 * </ul>
 */
class NodeDeliveryPollerTest {

    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private ListOperations<String, String> listOps;
    private ObjectMapper objectMapper;
    private OnlineDispatcherImpl onlineDispatcher;
    private NodeIdentityProvider nodeIdentityProvider;
    private NodeDeliveryPoller poller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        listOps = mock(ListOperations.class);
        objectMapper = new ObjectMapper();
        onlineDispatcher = mock(OnlineDispatcherImpl.class);
        nodeIdentityProvider = mock(NodeIdentityProvider.class);

        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(nodeIdentityProvider.getNodeId()).thenReturn("test-node-1");
    }

    @Test
    void pollLoopShouldDispatchWhenJsonReceived() throws Exception {
        DispatchMessageReq req = new DispatchMessageReq();
        req.setUserId("userB");

        String json = objectMapper.writeValueAsString(req);
        CountDownLatch latch = new CountDownLatch(1);

        // BRPOP: first call returns JSON, subsequent calls return null (timeout simulation)
        when(listOps.rightPop(eq(RedisKeys.deliveryNodeQueue("test-node-1")), eq(Duration.ofSeconds(30))))
                .thenAnswer(invocation -> {
                    if (latch.getCount() > 0) {
                        latch.countDown();
                        return json;
                    }
                    // After consuming the message, we stop the poller
                    poller.stop();
                    return null;
                });

        DispatchMessageResp resp = new DispatchMessageResp();
        resp.setResults(List.of(new DispatchResult("conn-1", true, "OK", "delivered")));
        when(onlineDispatcher.dispatchMessage(any(DispatchMessageReq.class))).thenReturn(resp);

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, nodeIdentityProvider);
        poller.start();

        // 等待消息消费完成
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        // 等待 stop 生效
        Thread.sleep(200);

        verify(onlineDispatcher, atLeastOnce()).dispatchMessage(any(DispatchMessageReq.class));
    }

    @Test
    void pollLoopShouldNotDispatchWhenTimeout() throws Exception {
        when(listOps.rightPop(eq(RedisKeys.deliveryNodeQueue("test-node-1")), eq(Duration.ofSeconds(30))))
                .thenAnswer(invocation -> {
                    // timeout → return null, then stop
                    poller.stop();
                    return null;
                });

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, nodeIdentityProvider);
        poller.start();

        // 等待 poller 完成
        Thread.sleep(300);

        verify(onlineDispatcher, never()).dispatchMessage(any(DispatchMessageReq.class));
    }

    @Test
    void pollLoopShouldSurviveMalformedJson() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        when(listOps.rightPop(eq(RedisKeys.deliveryNodeQueue("test-node-1")), eq(Duration.ofSeconds(30))))
                .thenAnswer(invocation -> {
                    if (latch.getCount() > 0) {
                        latch.countDown();
                        return "{invalid json";
                    }
                    poller.stop();
                    return null;
                });

        poller = new NodeDeliveryPoller(redisTemplate, objectMapper, onlineDispatcher, nodeIdentityProvider);
        poller.start();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);

        // 非法 JSON 不应导致 dispatch 调用
        verify(onlineDispatcher, never()).dispatchMessage(any(DispatchMessageReq.class));
    }
}
