package com.cheeseocean.im.postman.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OfflinePushExecutorConfigurationTest {

    @Test
    void saturatedExecutorShouldRejectInsteadOfUsingUnboundedOrCallerPool() throws Exception {
        OfflinePushExecutorProperties properties = new OfflinePushExecutorProperties();
        properties.setCorePoolSize(1);
        properties.setMaxPoolSize(1);
        properties.setQueueCapacity(1);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor)
                new OfflinePushExecutorConfiguration().offlinePushExecutor(properties);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> await(release));
            executor.execute(() -> await(release));

            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
