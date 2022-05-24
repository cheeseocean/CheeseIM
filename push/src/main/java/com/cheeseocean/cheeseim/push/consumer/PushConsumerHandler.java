package com.cheeseocean.cheeseim.push.consumer;

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
