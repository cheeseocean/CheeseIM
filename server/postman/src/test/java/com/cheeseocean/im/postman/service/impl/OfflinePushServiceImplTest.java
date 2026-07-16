package com.cheeseocean.im.postman.service.impl;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.postman.entity.OfflinePushResult;
import com.cheeseocean.im.postman.state.PushStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OfflinePushServiceImplTest {

    @Test
    void rejectedExecutorShouldReturnOneStableFailureWithoutRunningUserTask() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("queue full");
        };
        OfflinePushServiceImpl service = service(rejectingExecutor, 100L);

        OfflinePushResult result = service.pushMessageToUsers(message(), List.of("user-rejected"));

        assertEquals(List.of("user-rejected"), result.getFailedUsers());
        assertTrue(result.getSuccessUsers() == null || result.getSuccessUsers().isEmpty());
        assertEquals("离线推送服务繁忙", result.getUserErrors().get("user-rejected"));
    }

    @Test
    void neverCompletingExecutorShouldTimeoutAndReturnFrozenFailureSnapshot() {
        Executor neverRuns = command -> { /* 模拟已受理但永久不执行的饱和执行器。 */ };
        OfflinePushServiceImpl service = service(neverRuns, 100L);

        OfflinePushResult result = assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> service.pushMessageToUsers(message(), List.of("user-timeout")));

        assertEquals(List.of("user-timeout"), result.getFailedUsers());
        assertTrue(result.getSuccessUsers() == null || result.getSuccessUsers().isEmpty());
        assertEquals("离线推送执行超时", result.getUserErrors().get("user-timeout"));
        assertTrue(result.getProviderResults().isEmpty());
    }

    private OfflinePushServiceImpl service(Executor executor, long timeoutMillis) {
        return new OfflinePushServiceImpl(
                List.of(),
                mock(DeviceTokenServiceImpl.class),
                mock(StringRedisTemplate.class),
                mock(PushStateStore.class),
                executor,
                10,
                timeoutMillis);
    }

    private Message message() {
        Message message = new Message();
        message.setServerMsgId("message-1");
        return message;
    }
}
