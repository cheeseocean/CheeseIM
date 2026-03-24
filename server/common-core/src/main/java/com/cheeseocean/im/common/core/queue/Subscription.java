package com.cheeseocean.im.common.core.queue;

@FunctionalInterface
public interface Subscription {

    void unsubscribe();
}
