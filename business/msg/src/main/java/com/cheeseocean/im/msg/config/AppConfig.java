package com.cheeseocean.im.msg.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.Map;

/**
 * @author xxxcrel
 * @date 2023/4/17 15:53
 */
@Configuration
public class AppConfig {

    @Bean
    KafkaProducer<String, byte[]> msgProducer(MsgProperties msgProperties) {
        KafkaProducer<String, byte[]> msgProducer = new KafkaProducer<>(msgProperties.getKafka());
        return msgProducer;
    }
}
