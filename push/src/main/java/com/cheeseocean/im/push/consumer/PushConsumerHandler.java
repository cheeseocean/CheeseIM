package com.cheeseocean.im.push.consumer;

import com.cheeseocean.im.base.consumer.ConsumerHandler;

public class PushConsumerHandler implements ConsumerHandler {
    public PushConsumerHandler() {
    }

    @Override
    public boolean canHandle(String topic, String key) {
        return true;
    }

    @Override
    public void handle(String key, byte[] msg) {

    }
}
