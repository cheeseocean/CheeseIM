package com.cheeseocean.im.transfer.service;

import com.cheeseocean.im.base.consumer.ConsumerHandler;

public class HistoryMsgHandler implements ConsumerHandler {

    @Override
    public boolean canHandle(String topic, String key) {
        return false;
    }

    @Override
    public void handle(String key, byte[] msg) {

    }
}
