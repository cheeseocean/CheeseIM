package com.cheeseocean.im.common.constants;

public final class RedisKeys {

    private static final String PREFIX = "cheese_im";

    private RedisKeys() {
    }

    public static String onlineRoute(String userId) {
        return PREFIX + ":route:" + userId;
    }

    public static String idempotency(String senderId, String conversationId, String clientMsgId) {
        return PREFIX + ":delivery:idempotency:" + senderId + ":" + conversationId + ":" + clientMsgId;
    }

    public static String deliveryTask(String serverMsgId) {
        return PREFIX + ":delivery:task:" + serverMsgId;
    }
}
