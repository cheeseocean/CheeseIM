package com.cheeseocean.im.common.config;

import com.cheeseocean.im.common.serializer.GenericKafkaDeserializer;
import com.cheeseocean.im.common.serializer.KafkaMessageDeserializer;
import com.cheeseocean.im.common.serializer.KafkaMessageSerializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka序列化配置类
 * 配置Kafka的序列化器和反序列化器，支持byte数组格式的消息
 *
 * @author CheeseIM
 */
@Configuration
public class KafkaSerializationConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:cheese-im-group}")
    private String groupId;

    /**
     * 生产者配置 - 支持byte数组序列化
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // 基础配置
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaMessageSerializer.class);
        
        // 性能配置
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        // 压缩配置
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate - 用于发送消息
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * 消费者配置 - 支持byte数组反序列化
     */
    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // 基础配置
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
                ErrorHandlingDeserializer.class.getName());
        configProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, 
                org.apache.kafka.common.serialization.ByteArrayDeserializer.class.getName());
        
        // 消费配置
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Kafka监听器容器工厂 - 支持手动确认
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // 启用手动确认
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // 并发配置
        factory.setConcurrency(3);
        
        // 错误处理
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());
        
        return factory;
    }

    /**
     * 字符串消费者工厂 - 用于兼容现有的字符串消息
     */
    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-string");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * 字符串监听器容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> stringKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(stringConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(2);
        
        return factory;
    }

    /**
     * 泛型消费者工厂 - 支持指定类型的反序列化
     */
    public <T> ConsumerFactory<String, T> genericConsumerFactory(Class<T> valueType) {
        Map<String, Object> configProps = new HashMap<>();
        
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-" + valueType.getSimpleName().toLowerCase());
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, GenericKafkaDeserializer.class);
        configProps.put("value.deserializer.target.type", valueType.getName());
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * 创建指定类型的监听器容器工厂
     */
    public <T> ConcurrentKafkaListenerContainerFactory<String, T> createTypedListenerContainerFactory(Class<T> valueType) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(genericConsumerFactory(valueType));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(2);
        
        return factory;
    }
}
