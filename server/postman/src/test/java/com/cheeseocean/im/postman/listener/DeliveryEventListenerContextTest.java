package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.api.route.OnlineRouteQueryRpc;
import com.cheeseocean.im.common.api.rpc.OnlineDispatchRpc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class DeliveryEventListenerContextTest {

    @Test
    void deliveryEventListenerShouldBeCreatableInSpringContext() {
        assertDoesNotThrow(() -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.register(TestConfig.class, DeliveryEventListener.class);
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
        OnlineRouteQueryRpc onlineRouteQueryRpc() {
            return mock(OnlineRouteQueryRpc.class);
        }

        @Bean
        OnlineDispatchRpc onlineDispatchRpc() {
            return mock(OnlineDispatchRpc.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(KafkaTemplate.class);
        }
    }
}
