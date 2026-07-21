package com.cheeseocean.im.infra.queue.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka 队列写入配置。
 *
 * <p>事件经 {@code QueueAdapter} 传递原始字节；这里不提供对象或字符串模板，避免绕过队列序列化契约形成第二条消息路径。</p>
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnProperty(prefix = "cheeseim.queue", name = "type", havingValue = "kafka")
public class KafkaQueueConfiguration {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${cheeseim.queue.kafka.transaction-id-prefix:}")
    private String transactionIdPrefix;

    @Value("${spring.application.name:cheeseim}")
    private String applicationName;

    @Value("${HOSTNAME:local}")
    private String hostname;

    @Bean
    public ProducerFactory<String, byte[]> byteProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        DefaultKafkaProducerFactory<String, byte[]> producerFactory = new DefaultKafkaProducerFactory<>(configProps);
        producerFactory.setTransactionIdPrefix(resolveTransactionIdPrefix());
        return producerFactory;
    }

    private String resolveTransactionIdPrefix() {
        if (StringUtils.hasText(transactionIdPrefix)) {
            return transactionIdPrefix;
        }
        // transaction.id 必须在同一 Kafka 集群的并行 JVM 间唯一，不能只依赖可能重复的 hostname。
        return applicationName + "-" + hostname + "-" + UUID.randomUUID() + "-queue-";
    }

    @Bean
    public KafkaTemplate<String, byte[]> byteKafkaTemplate() {
        return new KafkaTemplate<>(byteProducerFactory());
    }
}
