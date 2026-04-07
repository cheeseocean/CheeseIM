package com.cheeseocean.im.common.core.queue;


public interface QueueAdapter {

    void send(String topic, String key, byte[] message);

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
}
