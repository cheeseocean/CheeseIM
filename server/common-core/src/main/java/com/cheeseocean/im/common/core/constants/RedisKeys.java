package com.cheeseocean.im.common.core.constants;

public final class RedisKeys {

    private static final String AUTH_PREFIX = "cheese_im";

    public static final String FIELD_GLOBAL_RECEIVE_OPTIONS = "user:receive_options:";

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

    public static String userFriendsLoaded(String userId) {
        return AUTH_PREFIX + ":user_friends_loaded:" + userId;
    }

    public static String userFriendRequests(String userId) {
        return AUTH_PREFIX + ":user_friend_requests:" + userId;
    }

    public static String userIncomingFriendRequests(String userId) {
        return AUTH_PREFIX + ":user_incoming_friend_requests:" + userId;
    }

    public static String userOutgoingFriendRequests(String userId) {
        return AUTH_PREFIX + ":user_outgoing_friend_requests:" + userId;
    }

    public static String friendRequest(String fromUserId, String toUserId) {
        return AUTH_PREFIX + ":friend_request:" + fromUserId + ":" + toUserId;
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

    public static String userSyncCheckpointReadSeq(String userId, String conversationId) {
        return AUTH_PREFIX + ":user_sync_checkpoint:read:" + userId + ":" + conversationId;
    }

    public static String userSyncCheckpointMaxSeq(String userId, String conversationId) {
        return AUTH_PREFIX + ":user_sync_checkpoint:max:" + userId + ":" + conversationId;
    }

    public static String userSyncCheckpointMinSeq(String userId, String conversationId) {
        return AUTH_PREFIX + ":user_sync_checkpoint:min:" + userId + ":" + conversationId;
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

    public static String userBlacklist(String userId) {
        return AUTH_PREFIX + ":user_blacklist:" + userId;
    }

    public static String userBlacklistLoaded(String userId) {
        return AUTH_PREFIX + ":user_blacklist_loaded:" + userId;
    }

    public static String userSettings(String userId) {
        return AUTH_PREFIX + ":user_settings:" + userId;
    }

    /**
     * 用户基础信息缓存 key（TTL 12h）。
     * 格式：cheese_im:user_info:{userId}
     */
    public static String userInfo(String userId) {
        return AUTH_PREFIX + ":user_info:" + userId;
    }

    /**
     * 用户所有会话 ID 集合缓存 key。
     * 格式：cheese_im:conv_ids:{userId}
     */
    public static String userConvIds(String userId) {
        return AUTH_PREFIX + ":conv_ids:" + userId;
    }

    /**
     * 用户会话 ID 集合已加载标记 key。
     * 用于表达“空集合已回源过”，避免无会话用户重复回源 MongoDB。
     * 格式：cheese_im:conv_ids_loaded:{userId}
     */
    public static String userConvIdsLoaded(String userId) {
        return AUTH_PREFIX + ":conv_ids_loaded:" + userId;
    }

    /**
     * 单条用户-会话业务状态缓存 key（TTL 12h）。
     * 存储完整的 UserConversation JSON，写操作触发 DEL 失效。
     * 格式：cheese_im:conv_state:{userId}:{conversationId}
     */
    public static String userConvState(String userId, String conversationId) {
        return AUTH_PREFIX + ":conv_state:" + userId + ":" + conversationId;
    }

    /**
     * 群组信息缓存 key（TTL 12h）。
     * 存储完整 Group 对象，写操作触发 DEL 失效。
     * 格式：cheese_im:group_info:{groupId}
     */
    public static String groupInfo(String groupId) {
        return AUTH_PREFIX + ":group_info:" + groupId;
    }

    /**
     * 群成员 ID 集合缓存 key（Redis SET，无 TTL，写时维护）。
     * 格式：cheese_im:group_member_ids:{groupId}
     */
    public static String groupMemberIds(String groupId) {
        return AUTH_PREFIX + ":group_member_ids:" + groupId;
    }

    public static String groupMemberIdsLoaded(String groupId) {
        return AUTH_PREFIX + ":group_member_ids_loaded:" + groupId;
    }

    /**
     * 单条群成员信息缓存 key（TTL 12h）。
     * 存储完整 GroupMember 对象，写操作触发 DEL 失效。
     * 格式：cheese_im:group_member_info:{groupId}:{userId}
     */
    public static String groupMemberInfo(String groupId, String userId) {
        return AUTH_PREFIX + ":group_member_info:" + groupId + ":" + userId;
    }

    /**
     * 用户已加入群组 ID 集合缓存 key（Redis SET，无 TTL，写时维护）。
     * 格式：cheese_im:user_joined_groups:{userId}
     */
    public static String userJoinedGroupIds(String userId) {
        return AUTH_PREFIX + ":user_joined_groups:" + userId;
    }

    public static String userJoinedGroupIdsLoaded(String userId) {
        return AUTH_PREFIX + ":user_joined_groups_loaded:" + userId;
    }

    /**
     * 群成员数量缓存 key（TTL 12h，String 存整数字符串）。
     * 写操作触发 DEL 失效，下次读取时回源 MongoDB 重建。
     * 格式：cheese_im:group_member_num:{groupId}
     */
    public static String groupMemberNum(String groupId) {
        return AUTH_PREFIX + ":group_member_num:" + groupId;
    }

    /**
     * 群内指定角色成员 ID 集合缓存 key（Redis SET，无 TTL，写时失效）。
     * 格式：cheese_im:group_role_members:{groupId}:{roleLevel}
     */
    public static String groupRoleLevelMemberIds(String groupId, int roleLevel) {
        return AUTH_PREFIX + ":group_role_members:" + groupId + ":" + roleLevel;
    }
}
