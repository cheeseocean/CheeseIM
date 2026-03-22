package com.cheeseocean.im.common.core.util;

import com.cheeseocean.im.common.core.enums.SessionType;

public final class ConversationIdUtil {

    private ConversationIdUtil() {
    }

    public static String buildConversationId(int sessionType, String senderId, String recvId, String groupId) {
        if (sessionType == SessionType.SINGLE) {
            return single(senderId, recvId);
        }
        if (sessionType == SessionType.GROUP) {
            return group(groupId);
        }
        if (sessionType == SessionType.NOTIFICATION) {
            return notification(recvId);
        }
        throw new IllegalArgumentException("unknown sessionType: " + sessionType);
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
