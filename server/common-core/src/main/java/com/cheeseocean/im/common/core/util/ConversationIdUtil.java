package com.cheeseocean.im.common.core.util;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.ChatType;

public final class ConversationIdUtil {

    private ConversationIdUtil() {
    }

    public static String buildConversationId(Message message) {
        return buildConversationId(message.getChatType(), message.getSenderId(), message.getReceiverId(), message.getGroupId());
    }

    public static String buildConversationId(ChatType chatType, String senderId, String receiverId, String groupId) {
        if (chatType == null) {
            throw new IllegalArgumentException("chatType required");
        }
        return switch (chatType) {
            case PRIVATE -> single(senderId, receiverId);
            case GROUP -> group(groupId);
            case NOTIFICATION -> notification(receiverId);
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
    public static String buildQueueKey(ChatType chatType, String senderId, String receiverId, String groupId) {
        return switch (chatType) {
            case GROUP -> groupId;
            case PRIVATE, NOTIFICATION -> senderId.compareTo(receiverId) < 0
                    ? senderId + ":" + receiverId
                    : receiverId + ":" + senderId;
        };
    }

    public static String buildNotificationConversationId(Message message) {
        return buildNotificationConversationId(message.getChatType(), message.getReceiverId(), message.getGroupId());
    }

    /**
     * 计算通知会话 conversationId。
     * 与 buildConversationId 使用各自独立的 seq 计数器：
     *   SINGLE / NOTIFICATION → c3:{recvId}（接收方的通知收件箱）
     *   GROUP                 → c3:{groupId}（群通知频道）
     */
    public static String buildNotificationConversationId(ChatType chatType, String receiverId, String groupId) {
        return switch (chatType) {
            case PRIVATE, NOTIFICATION -> notification(receiverId);
            case GROUP -> "ng:" + groupId;
        };
    }

    public static String single(String userA, String userB) {
        if (userA == null || userB == null) {
            throw new IllegalArgumentException("single conversation users required");
        }
        return userA.compareTo(userB) < 0
                ? "s:" + userA + ":" + userB
                : "s:" + userB + ":" + userA;
    }

    public static String group(String groupId) {
        return "g:" + groupId;
    }

    public static String notification(String userId) {
        return "n:" + userId;
    }

    public static String peerUser(String conversationId, String currentUserId) {
        if (conversationId == null || currentUserId == null || !conversationId.startsWith("s:")) {
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
