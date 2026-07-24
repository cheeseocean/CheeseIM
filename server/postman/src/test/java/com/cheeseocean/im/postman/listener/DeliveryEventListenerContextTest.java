package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.route.OnlineRouteQueryService;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.postman.delivery.OfflinePushEventFactory;
import com.cheeseocean.im.postman.sender.OfflinePushEventProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class DeliveryEventListenerContextTest {

    @Test
    void deliveryEventListenerShouldBeCreatableInSpringContext() {
        assertDoesNotThrow(() -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.register(TestConfig.class, DeliveryEventListener.class,
                        OfflinePushEventProducer.class, OfflinePushEventFactory.class);
                context.refresh();
                context.getBean(DeliveryEventListener.class);
            }
        });
    }

    @Configuration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        OnlineRouteQueryService onlineRouteQueryRpc() {
            return mock(OnlineRouteQueryService.class);
        }

        @Bean
        OnlineDispatcher onlineDispatchRpc() {
            return mock(OnlineDispatcher.class);
        }

        @Bean
        QueueAdapter queueAdapter() {
            return mock(QueueAdapter.class);
        }
    }
}
