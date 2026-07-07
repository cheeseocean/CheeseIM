package com.cheeseocean.im.common.core.queue.kafka;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ProtoEnvelopeMapper;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.core.queue.KeyedMessage;
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

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaProperties kafkaProperties;

    public KafkaQueueAdapter(KafkaTemplate<String, byte[]> kafkaTemplate,
                             ObjectMapper objectMapper,
                             KafkaProperties kafkaProperties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.kafkaProperties = kafkaProperties;
    }

    @Override
    public void send(String topic, String key, byte[] message) {
        try {
            kafkaTemplate.send(topic, key, message);
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
        containerProperties.setMessageListener((org.springframework.kafka.listener.MessageListener<String, byte[]>) record -> {
            try {
                handler.handle(deserialize(record.value(), payloadType));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize Kafka queue message", e);
            }
        });
        ConcurrentMessageListenerContainer<String, byte[]> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory(group), containerProperties);
        container.setConcurrency(Math.max(1, concurrency));
        container.start();
        return container::stop;
    }

    @Override
    public <T> Subscription subscribeKeyed(String topic, String group, int concurrency, Class<T> payloadType, QueueMessageHandler<KeyedMessage<T>> handler) {
        ContainerProperties containerProperties = new ContainerProperties(topic);
        containerProperties.setGroupId(group);
        containerProperties.setMessageListener((org.springframework.kafka.listener.MessageListener<String, byte[]>) record -> {
            try {
                handler.handle(new KeyedMessage<>(record.key(), deserialize(record.value(), payloadType)));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize Kafka queue message", e);
            }
        });
        ConcurrentMessageListenerContainer<String, byte[]> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory(group), containerProperties);
        container.setConcurrency(Math.max(1, concurrency));
        container.start();
        return container::stop;
    }

    /**
     * 反序列化策略与 {@link com.cheeseocean.im.common.core.queue.chronicle.ChronicleQueueAdapter#deserialize}
     * 对齐，保证 Kafka / Chronicle 两种队列后端对同一 payloadType 行为一致：
     * <ul>
     *   <li>{@code byte[]} → 透传原字节，由消费侧自行解析 protobuf（见
     *       {@link com.cheeseocean.im.postman.listener.OfflinePushEventListener#onMessage(byte[])}）</li>
     *   <li>{@link com.cheeseocean.im.common.api.dto.message.Message} / {@link com.cheeseocean.im.common.api.event.HistoryEvent} /
     *       {@link com.cheeseocean.im.common.api.event.OfflinePushEvent} → 同样走 protobuf 原生解析，
     *       修复 ASSESSMENT P1-6 提到的"Kafka 路径用 Jackson，Producer 发 Protobuf 字节端到端不兼容"</li>
     *   <li>其它类型 → Jackson 兜底（兼容单元测试中传入的任意 DTO）</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private <T> T deserialize(byte[] payload, Class<T> payloadType) throws Exception {
        if (payloadType == byte[].class) {
            return (T) payload;
        }
        if (payloadType == com.cheeseocean.im.common.api.dto.message.Message.class) {
            return (T) com.cheeseocean.im.common.api.protocol.ProtoMessageMapper.fromProto(
                    com.cheeseocean.im.common.api.protocol.proto.ProtoMessage.parseFrom(payload));
        }
        if (payloadType == com.cheeseocean.im.common.api.event.HistoryEvent.class) {
            return (T) com.cheeseocean.im.common.api.protocol.ProtoHistoryEventMapper.fromProto(
                    com.cheeseocean.im.common.api.protocol.proto.ProtoHistoryEvent.parseFrom(payload));
        }
        if (payloadType == com.cheeseocean.im.common.api.event.OfflinePushEvent.class) {
            return (T) com.cheeseocean.im.common.api.protocol.ProtoOfflinePushEventMapper.fromProto(
                    com.cheeseocean.im.common.api.protocol.proto.ProtoOfflinePushEvent.parseFrom(payload));
        }
        return objectMapper.readValue(payload, payloadType);
    }

    private ConsumerFactory<String, byte[]> consumerFactory(String group) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        config.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        config.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return new DefaultKafkaConsumerFactory<>(config);
    }
}
