package com.cheeseocean.im.msg.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * @author xxxcrel
 * @date 2023/4/17 15:53
 */
@Configuration
public class AppConfig {
    @Value("${kafka}")
    Map<String, Object> kafkaProps;
    @Bean
    KafkaProducer<String, byte[]> msgProducer() {
        KafkaProducer<String, byte[]> msgProducer = new KafkaProducer<>(kafkaProps);
        return msgProducer;
    }
}
