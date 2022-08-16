package com.cheeseocean.im.base.consumer;

public interface ConsumerHandler {
    boolean canHandle(String topic, String key);
    void handle(String key, byte[] msg);
}
