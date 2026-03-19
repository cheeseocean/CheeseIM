package com.cheeseocean.im.common.constants;

public final class RedisKeys {

    private static final String PREFIX = "cheese_im";
    public static final String WS_TICKET = PREFIX + ":ws_ticket:";
    public static final String CONN = PREFIX + ":conn:";
    public static final String USER_CONNS = PREFIX + ":user_conns:";
    public static final String SESSION_CONNS = PREFIX + ":session_conns:";
    public static final String DEVICE_CONNS = PREFIX + ":device_conns:";
    public static final String USER_SESSION = PREFIX + ":user_session:";
    public static final String USER_SESSIONS = PREFIX + ":user_sessions:";
    public static final String DEVICE_SESSION = PREFIX + ":device_session:";
    public static final String USER_SECURITY = PREFIX + ":user_security:";
    public static final String USER_FRIENDS = PREFIX + ":user_friends:";
    public static final String USER_FRIEND_REQUESTS = PREFIX + ":user_friend_requests:";
    public static final String KICKOFF_EVENT = PREFIX + ":kickoff:event:";

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
