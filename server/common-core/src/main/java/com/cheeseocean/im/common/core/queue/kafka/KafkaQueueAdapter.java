package com.cheeseocean.im.common.core.queue.kafka;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ProtoEnvelopeMapper;
import com.cheeseocean.im.common.api.protocol.ProtoMessageMapper;
import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueMessageHandler;
import com.cheeseocean.im.common.core.queue.Subscription;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.BatchMessageListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class KafkaQueueAdapter implements QueueAdapter {

    static final long RETRY_INTERVAL_MS = 1_000L;
    static final long RETRY_ATTEMPTS = 3L;

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
        long started = ImMetrics.startTimer();
        try {
            awaitBrokerAck(kafkaTemplate.send(topic, key, message), "message");
            ImMetrics.queuePublish("kafka", topic, true, started);
        } catch (RuntimeException exception) {
            ImMetrics.queuePublish("kafka", topic, false, started);
            throw exception;
        }
    }

    @Override
    public void sendBatch(String topic, List<KeyedMessage<byte[]>> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        long started = ImMetrics.startTimer();
        try {
            kafkaTemplate.executeInTransaction(operations -> {
                List<CompletableFuture<SendResult<String, byte[]>>> sends = messages.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(message -> operations.send(topic, message.key(), message.payload()))
                        .toList();
                for (CompletableFuture<SendResult<String, byte[]>> send : sends) {
                    awaitBrokerAck(send, "message batch");
                }
                return null;
            });
            ImMetrics.queuePublish("kafka", topic, true, started);
        } catch (RuntimeException exception) {
            ImMetrics.queuePublish("kafka", topic, false, started);
            throw exception;
        }
    }

    private void awaitBrokerAck(CompletableFuture<SendResult<String, byte[]>> send, String operation) {
        try {
            send.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing Kafka queue " + operation, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Kafka broker rejected queue " + operation, cause);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to publish Kafka queue " + operation, e);
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
        configureReliableAcknowledgment(containerProperties);
        containerProperties.setMessageListener((org.springframework.kafka.listener.MessageListener<String, byte[]>) record -> {
            try {
                handler.handle(deserialize(record.value(), payloadType));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize Kafka queue message", e);
            }
        });
        ConcurrentMessageListenerContainer<String, byte[]> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory(group), containerProperties);
        configureFailureHandling(container);
        container.setConcurrency(Math.max(1, concurrency));
        container.start();
        return container::stop;
    }

    @Override
    public <T> Subscription subscribeKeyed(String topic, String group, int concurrency, Class<T> payloadType, QueueMessageHandler<KeyedMessage<T>> handler) {
        ContainerProperties containerProperties = new ContainerProperties(topic);
        containerProperties.setGroupId(group);
        configureReliableAcknowledgment(containerProperties);
        containerProperties.setMessageListener((org.springframework.kafka.listener.MessageListener<String, byte[]>) record -> {
            try {
                handler.handle(new KeyedMessage<>(record.key(), deserialize(record.value(), payloadType)));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize Kafka queue message", e);
            }
        });
        ConcurrentMessageListenerContainer<String, byte[]> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory(group), containerProperties);
        configureFailureHandling(container);
        container.setConcurrency(Math.max(1, concurrency));
        container.start();
        return container::stop;
    }

    @Override
    public <T> Subscription subscribeBatch(String topic, String group, int concurrency, int batchSize,
                                           long batchIntervalMs, Class<T> payloadType,
                                           QueueMessageHandler<List<KeyedMessage<T>>> handler) {
        ContainerProperties properties = new ContainerProperties(topic);
        properties.setGroupId(group);
        properties.setAckMode(ContainerProperties.AckMode.BATCH);
        properties.setSyncCommits(true);
        properties.setMessageListener((BatchMessageListener<String, byte[]>) records -> {
            try {
                List<KeyedMessage<T>> batch = new java.util.ArrayList<>(records.size());
                for (org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> record : records) {
                    batch.add(new KeyedMessage<>(record.key(), deserialize(record.value(), payloadType)));
                }
                handler.handle(batch);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to process Kafka queue batch", e);
            }
        });
        ConcurrentMessageListenerContainer<String, byte[]> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory(group, batchSize), properties);
        configureFailureHandling(container);
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
        return consumerFactory(group, null);
    }

    private ConsumerFactory<String, byte[]> consumerFactory(String group, Integer maxPollRecords) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        config.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        config.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        if (maxPollRecords != null) {
            config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        }
        return new DefaultKafkaConsumerFactory<>(config);
    }

    void configureReliableAcknowledgment(ContainerProperties containerProperties) {
        // 业务 handler 正常返回后才同步提交该记录；异常路径不会推进 offset。
        containerProperties.setAckMode(ContainerProperties.AckMode.RECORD);
        containerProperties.setSyncCommits(true);
    }

    void configureFailureHandling(ConcurrentMessageListenerContainer<String, byte[]> container) {
        container.setCommonErrorHandler(createErrorHandler());
    }

    DefaultErrorHandler createErrorHandler() {
        return createErrorHandler(new FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS));
    }

    DefaultErrorHandler createErrorHandler(FixedBackOff backOff) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, this::dltDestination);
        // DLT broker ACK 失败时继续抛出，不能在毒消息尚未可靠落入 DLT 时推进原 topic offset。
        recoverer.setFailIfSendResultIsError(true);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer, backOff);
        errorHandler.setAckAfterHandle(true);
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    TopicPartition dltDestination(ConsumerRecord<?, ?> record, Exception exception) {
        return new TopicPartition(record.topic() + ".DLT", record.partition());
    }
}
