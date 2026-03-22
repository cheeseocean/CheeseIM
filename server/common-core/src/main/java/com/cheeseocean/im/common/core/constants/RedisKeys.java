package com.cheeseocean.im.common.core.constants;

public final class RedisKeys {

    private RedisKeys() {
    }

    public static String onlineUser(String userId) {
        return "online:user:" + userId;
    }

    public static String onlineConn(String connectionId) {
        return "online:conn:" + connectionId;
    }

    public static String convMaxSeq(String conversationId) {
        return "conv:maxSeq:" + conversationId;
    }

    public static String convMinSeq(String conversationId) {
        return "conv:minSeq:" + conversationId;
    }

    public static String convLastMsg(String conversationId) {
        return "conv:lastMsg:" + conversationId;
    }

    public static String userReadSeq(String userId, String conversationId) {
        return "uc:read:" + userId + ":" + conversationId;
    }

    public static String userMinSeq(String userId, String conversationId) {
        return "uc:min:" + userId + ":" + conversationId;
    }

    public static String userMaxSeq(String userId, String conversationId) {
        return "uc:max:" + userId + ":" + conversationId;
    }

    public static String userUnread(String userId, String conversationId) {
        return "uc:unread:" + userId + ":" + conversationId;
    }

    public static String msgCache(String conversationId, long seq) {
        return "msg:" + conversationId + ":" + seq;
    }

    public static String ingressIdem(String conversationId, String clientMsgId) {
        return "idem:ingress:" + conversationId + ":" + clientMsgId;
    }

    public static String postmanIdem(String conversationId, String clientMsgId) {
        return "idem:postman:" + conversationId + ":" + clientMsgId;
    }

    public static String deliveryIdem(String serverMsgId, String userId, String connectionId) {
        return "idem:delivery:" + serverMsgId + ":" + userId + ":" + connectionId;
    }
}
