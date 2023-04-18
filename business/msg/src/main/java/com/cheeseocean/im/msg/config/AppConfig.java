package com.cheeseocean.im.msg.config;

import org.apache.dubbo.config.spring.context.annotation.DubboComponentScan;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.HashMap;
import java.util.Properties;

/**
 * @author xxxcrel
 * @date 2023/4/17 15:53
 */
@Configuration
@ComponentScan(basePackages = "com.cheeseocean.im.msg")
@EnableDubbo
@DubboComponentScan(basePackages = "com.cheeseocean.im.msg.service")
public class AppConfig {

    @Bean("msgProducer")
    KafkaProducer<String, byte[]> msgProducer(MsgProperties msgProperties) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, msgProperties.getBootstrapServers());
        return new KafkaProducer<>(properties, new StringSerializer(), new ByteArraySerializer());
    }
}
