package com.cheeseocean.im.common.core.queue;

@FunctionalInterface
public interface QueueMessageHandler<T> {

    void handle(T message);
}
