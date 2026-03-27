package com.cheeseocean.im.common.core.queue;

public record KeyedMessage<T>(String key, T payload) {
}
