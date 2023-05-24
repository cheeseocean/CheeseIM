package com.cheeseocean.im.message.config;

import com.cheeseocean.im.common.config.YamlPropertySourceFactory;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.Properties;

/**
 * @author xxxcrel
 * @date 2023/4/17 15:53
 */
@Configuration
@PropertySource(value = "classpath:message.yml", factory = YamlPropertySourceFactory.class)
public class MessageConfig {

    @Value("${message.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    KafkaProducer<String, byte[]> messageProducer() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaProducer<>(properties, new StringSerializer(), new ByteArraySerializer());
    }
}
