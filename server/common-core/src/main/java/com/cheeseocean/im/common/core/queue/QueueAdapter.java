package com.cheeseocean.im.common.core.queue;

import java.util.List;

public interface QueueAdapter {

    void send(String topic, String key, byte[] message);

    /**
     * 批量发送同一 topic 的消息。
     *
     * <p>默认实现保持所有现有适配器兼容；具体队列后端可覆盖此方法，复用 producer/appender
     * 的批处理能力。调用方必须提前保证相同 key 的消息顺序。
     */
    default void sendBatch(String topic, List<KeyedMessage<byte[]>> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (KeyedMessage<byte[]> message : messages) {
            if (message != null) {
                send(topic, message.key(), message.payload());
            }
        }
    }

    <T> Subscription subscribe(
            String topic,
            String group,
            int concurrency,
            Class<T> payloadType,
            QueueMessageHandler<T> handler
    );

    default <T> Subscription subscribeKeyed(
            String topic,
            String group,
            int concurrency,
            Class<T> payloadType,
            QueueMessageHandler<KeyedMessage<T>> handler
    ) {
        throw new UnsupportedOperationException("subscribeKeyed is not supported by this adapter");
    }

    /**
     * 订阅原生批次。实现必须在 handler 成功返回前保留消费确认，并保证单批不超过 batchSize。
     */
    default <T> Subscription subscribeBatch(
            String topic, String group, int concurrency, int batchSize, long batchIntervalMs,
            Class<T> payloadType, QueueMessageHandler<List<KeyedMessage<T>>> handler) {
        throw new UnsupportedOperationException("subscribeBatch is not supported by this adapter");
    }
}
