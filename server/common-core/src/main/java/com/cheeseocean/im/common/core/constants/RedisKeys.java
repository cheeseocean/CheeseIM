package com.cheeseocean.im.common.core.constants;

public final class RedisKeys {

    private static final String AUTH_PREFIX = "cheese_im";

    public static final String USER_RECEIVE_OPTIONS_PREFIX = "user:receive_options:";
    public static final String USER_INFO_PREFIX = "user:info:";

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

    /**
     * 用户在线路由表缓存 key。
     *
     * <p>存储结构：Redis HASH（field = {@code route:{deviceId}} / {@code heartbeat:{deviceId}}），
     * 详见 {@code postoffice/RedisOnlineRouteService}。
     *
     * <p>版本化原因：早期实现把该 key 写成 String，
     * 2026-07-06 P0-3 路由表原子化改为 Lua + HASH。若直接复用旧 key，
     * 滚动重启期间旧 String 残留会导致 {@code HSET}/{@code HGETALL} 抛 {@code WRONGTYPE}，
     * 在旧 key 30min TTL 自然过期前影响该用户的注册/心跳/注销链路。
     * 加 {@code v2} 后旧 key 自然过期，无需手动清理。
     */
    public static String onlineUser(String userId) {
        return "online:user:v2:" + userId;
    }

    /**
     * 会话到在线路由的辅助索引。
     *
     * <p>踢下线命令有时只携带 sessionId，无法从 {@code online:user:v2:{userId}} 反查。
     * postoffice 在连接注册时写入该 key，让 session revoke 可以按 gatewayNode 定向到持有连接的节点。
     * 该 key 使用 Redis HASH，field 为 {@code userId:deviceId}，value 为 {@code RouteSnapshot} JSON，
     * 同一 session 多连接/多节点时可以保留多条路由。
     */
    public static String onlineSession(String sessionId) {
        return "online:session:v1:" + sessionId;
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

    public static String deviceDeliveredSeq(String userId, String deviceId, String conversationId) {
        return "uc:delivered:" + userId + ":" + deviceId + ":" + conversationId;
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

    /**
     * postman 离线推送的跨副本状态。
     *
     * <p>一个 serverMsgId 对应一个 HASH，field 按 userId 区分 attempt 与 delivery state，
     * 以便 Lua 在单 key 内原子判断“已读/已确认”和“是否已有推送尝试”。
     */
    public static String postmanPushState(String serverMsgId) {
        return "push:state:" + serverMsgId;
    }

    /** 用户在指定自然日内已占用的离线推送配额。 */
    public static String postmanDailyPushQuota(String userId, String date) {
        return "push:daily:" + date + ":" + userId;
    }

    /**
     * HTTP API 固定窗口限流计数器。
     *
     * <p>来源地址在进入 Redis 前已散列，避免把原始网络地址作为持久化 key 的一部分。
     */
    public static String apiRateLimit(String clientFingerprint, long windowBucket) {
        return "rate:api:" + clientFingerprint + ":" + windowBucket;
    }

    /** HTTP 写接口幂等占位 key。 */
    public static String apiIdempotency(String userId, String method, String path, String keyFingerprint) {
        return "idem:api:" + userId + ":" + method + ":" + path + ":" + keyFingerprint;
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

    /**
     * 按节点队列 key（P0-1 跨节点在线投递修复 + P1 跨节点踢下线）。
     *
     * <p>存储结构：Redis LIST。
     * 生产者统一通过 {@code NodeQueueMessage} envelope 入队；postoffice 使用可靠 LIST
     * 将消息从 ready 原子搬到 processing，执行成功后再 ACK 删除。
     *
     * <p>格式：delivery:node:{nodeId}
     *
     * @param nodeId 目标 postoffice 节点标识（来自 RouteSnapshot.gatewayNode）
     */
    public static String deliveryNodeQueue(String nodeId) {
        return "delivery:node:" + nodeId;
    }

    /** 节点队列处理中列表，供 ACK、失败重试和进程重启恢复。 */
    public static String deliveryNodeProcessingQueue(String nodeId) {
        return deliveryNodeQueue(nodeId) + ":processing";
    }

    /** 节点队列死信列表，保存超过重试上限或无法解析的消息。 */
    public static String deliveryNodeDeadLetterQueue(String nodeId) {
        return deliveryNodeQueue(nodeId) + ":dead";
    }
}
