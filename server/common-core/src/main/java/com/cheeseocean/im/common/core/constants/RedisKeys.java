package com.cheeseocean.im.common.core.constants;

public final class RedisKeys {

    private static final String AUTH_PREFIX = "cheese_im";

    private RedisKeys() {
    }

    public static String wsTicket(String ticket) {
        return AUTH_PREFIX + ":ws_ticket:" + ticket;
    }

    public static String userSession(String sessionId) {
        return AUTH_PREFIX + ":user_session:" + sessionId;
    }

    public static String userSessions(String userId) {
        return AUTH_PREFIX + ":user_sessions:" + userId;
    }

    public static String deviceSession(String userId, String deviceId) {
        return AUTH_PREFIX + ":device_session:" + userId + ":" + deviceId;
    }

    public static String userSecurity(String userId) {
        return AUTH_PREFIX + ":user_security:" + userId;
    }

    public static String userFriends(String userId) {
        return AUTH_PREFIX + ":user_friends:" + userId;
    }

    public static String userFriendRequests(String userId) {
        return AUTH_PREFIX + ":user_friend_requests:" + userId;
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

    public static String consumerDedup(String consumerGroup, String eventId) {
        return "idem:consumer:" + consumerGroup + ":" + eventId;
    }
}
