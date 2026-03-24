package com.cheeseocean.im.common.core.queue.kafka;

import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueMessageHandler;
import com.cheeseocean.im.common.core.queue.Subscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

public class KafkaQueueAdapter implements QueueAdapter {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaProperties kafkaProperties;

    public KafkaQueueAdapter(KafkaTemplate<String, String> kafkaTemplate,
                             ObjectMapper objectMapper,
                             KafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.kafkaProperties = kafkaProperties;
    }

    @Override
    public <T> void send(String topic, String key, T message) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish Kafka queue message", e);
        }
    }

    @Override
    public <T> Subscription subscribe(String topic,
                                      String group,
                                      int concurrency,
                                      Class<T> payloadType,
                                      QueueMessageHandler<T> handler) {
        ContainerProperties containerProperties = new ContainerProperties(topic);
        containerProperties.setGroupId(group);
        containerProperties.setMessageListener((org.springframework.kafka.listener.MessageListener<String, String>) record -> {
            try {
                handler.handle(objectMapper.readValue(record.value(), payloadType));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize Kafka queue message", e);
            }
        });
        ConcurrentMessageListenerContainer<String, String> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory(group), containerProperties);
        container.setConcurrency(Math.max(1, concurrency));
        container.start();
        return container::stop;
    }

    private ConsumerFactory<String, String> consumerFactory(String group) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        config.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new DefaultKafkaConsumerFactory<>(config);
    }
}
