package com.cheeseocean.im.common.entity;

public enum DeliveryState {
    INIT,
    PERSISTED,
    ROUTED,
    ONLINE_DELIVERING,
    ONLINE_CONFIRMED,
    INBOXED,
    PUSH_TRIGGERED,
    READ,
    RECALLED,
    FAILED_RECOVERABLE,
    FAILED_FINAL
}
