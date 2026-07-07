package com.cheeseocean.im.common.core.queue.config;

import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.chronicle.ChronicleQueueAdapter;
import com.cheeseocean.im.common.core.queue.kafka.KafkaQueueAdapter;
import com.cheeseocean.im.common.core.queue.processor.QueueListenerBeanPostProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@EnableConfigurationProperties(QueueProperties.class)
public class QueueAutoConfigurer {

    @Bean
    @ConditionalOnMissingBean
    public static QueueListenerBeanPostProcessor queueListenerBeanPostProcessor() {
        return new QueueListenerBeanPostProcessor();
    }

    @Bean
    @ConditionalOnMissingBean(QueueAdapter.class)
    @ConditionalOnProperty(prefix = "cheeseim.queue", name = "type", havingValue = "chronicle", matchIfMissing = true)
    public QueueAdapter chronicleQueueAdapter(ObjectMapper objectMapper, QueueProperties queueProperties) {
        return new ChronicleQueueAdapter(objectMapper, queueProperties);
    }

    @Bean
    @ConditionalOnMissingBean(QueueAdapter.class)
    @ConditionalOnProperty(prefix = "cheeseim.queue", name = "type", havingValue = "kafka")
    public QueueAdapter kafkaQueueAdapter(KafkaTemplate<String, byte[]> byteKafkaTemplate,
                                          ObjectMapper objectMapper,
                                          KafkaProperties kafkaProperties) {
        return new KafkaQueueAdapter(byteKafkaTemplate, objectMapper, kafkaProperties);
    }
}
