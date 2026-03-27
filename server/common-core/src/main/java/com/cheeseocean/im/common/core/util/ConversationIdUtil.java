package com.cheeseocean.im.common.core.util;

import com.cheeseocean.im.common.core.enums.SessionType;

public final class ConversationIdUtil {

    private ConversationIdUtil() {
    }

    public static String buildConversationId(int sessionType, String senderId, String recvId, String groupId) {
        SessionType type = SessionType.fromCode(sessionType);
        return switch (type) {
            case SINGLE -> single(senderId, recvId);
            case GROUP -> group(groupId);
            case NOTIFICATION -> notification(recvId);
        };
    }

    /**
     * 计算 INGRESS 队列的分区 key
     * <p>
     * 重要：single chat 和 notification 消息使用相同的 key，会被归入同一批次，
     * 由消费方的 handleMsg / handleNotification 在批次内分别处理。
     *   SINGLE / NOTIFICATION → sort(senderId, recvId) 拼接，无前缀
     *   GROUP                 → groupId，无前缀
     */
    public static String buildQueueKey(int sessionType, String senderId, String recvId, String groupId) {
        SessionType type = SessionType.fromCode(sessionType);
        return switch (type) {
            case GROUP -> groupId;
            case SINGLE, NOTIFICATION -> senderId.compareTo(recvId) < 0
                    ? senderId + ":" + recvId
                    : recvId + ":" + senderId;
        };
    }

    /**
     * 计算通知会话 conversationId，对应 Go 的 GetNotificationConversationIDByMsg。
     * 与 buildConversationId 使用各自独立的 seq 计数器：
     *   SINGLE / NOTIFICATION → c3:{recvId}（接收方的通知收件箱）
     *   GROUP                 → c3:{groupId}（群通知频道）
     */
    public static String buildNotificationConversationId(int sessionType, String recvId, String groupId) {
        SessionType type = SessionType.fromCode(sessionType);
        return switch (type) {
            case SINGLE, NOTIFICATION -> notification(recvId);
            case GROUP -> "c3:" + groupId;
        };
    }

    public static String single(String userA, String userB) {
        if (userA == null || userB == null) {
            throw new IllegalArgumentException("single conversation users required");
        }
        return userA.compareTo(userB) < 0
                ? "c1:" + userA + ":" + userB
                : "c1:" + userB + ":" + userA;
    }

    public static String group(String groupId) {
        return "c2:" + groupId;
    }

    public static String notification(String userId) {
        return "c3:" + userId;
    }

    public static String peerUser(String conversationId, String currentUserId) {
        if (conversationId == null || currentUserId == null || !conversationId.startsWith("c1:")) {
            return null;
        }
        String[] parts = conversationId.split(":");
        if (parts.length != 3) {
            return null;
        }
        if (currentUserId.equals(parts[1])) {
            return parts[2];
        }
        if (currentUserId.equals(parts[2])) {
            return parts[1];
        }
        return null;
    }
}
