package com.cheeseocean.im.infra.queue.config;

import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.infra.queue.chronicle.ChronicleQueueAdapter;
import com.cheeseocean.im.infra.queue.kafka.KafkaQueueAdapter;
import com.cheeseocean.im.common.core.queue.dlt.DltOperations;
import com.cheeseocean.im.common.core.queue.dlt.DltRedriveAuditStore;
import com.cheeseocean.im.infra.queue.dlt.KafkaDltOperations;
import com.cheeseocean.im.infra.queue.processor.QueueListenerBeanPostProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import com.cheeseocean.im.infra.queue.kafka.KafkaQueueConfiguration;
import com.cheeseocean.im.infra.queue.kafka.KafkaTopicConfiguration;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration(after = {KafkaQueueConfiguration.class, KafkaTopicConfiguration.class})
@EnableConfigurationProperties(QueueProperties.class)
public class QueueInfrastructureAutoConfiguration {

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
                                          KafkaProperties kafkaProperties,
                                          QueueProperties queueProperties) {
        return new KafkaQueueAdapter(byteKafkaTemplate, objectMapper, kafkaProperties, queueProperties);
    }

    @Bean
    @ConditionalOnBean(value = DltRedriveAuditStore.class, name = "byteKafkaTemplate")
    @ConditionalOnMissingBean(DltOperations.class)
    @ConditionalOnProperty(prefix = "cheeseim.queue.dlt.operations", name = "enabled", havingValue = "true")
    public DltOperations kafkaDltOperations(
            KafkaTemplate<String, byte[]> byteKafkaTemplate,
            KafkaProperties kafkaProperties,
            DltRedriveAuditStore auditStore,
            org.springframework.core.env.Environment environment) {
        return new KafkaDltOperations(
                byteKafkaTemplate,
                kafkaProperties,
                auditStore,
                environment.getProperty(
                        "cheeseim.queue.dlt.operations.max-query-records",
                        Integer.class,
                        100),
                environment.getProperty(
                        "cheeseim.queue.dlt.operations.poll-timeout-millis",
                        Long.class,
                        3_000L),
                environment.getProperty(
                        "cheeseim.queue.dlt.operations.lease-millis",
                        Long.class,
                        60_000L));
    }
}
