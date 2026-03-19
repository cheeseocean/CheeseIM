package com.cheeseocean.im.common.utils;

public final class ConversationIds {

    private ConversationIds() {
    }

    public static String direct(String userA, String userB) {
        if (userA == null || userA.isBlank() || userB == null || userB.isBlank()) {
            throw new IllegalArgumentException("direct conversation users required");
        }
        return userA.compareTo(userB) <= 0
                ? "single:" + userA + ":" + userB
                : "single:" + userB + ":" + userA;
    }

    public static String peerUser(String conversationId, String currentUserId) {
        if (conversationId == null || currentUserId == null || !conversationId.startsWith("single:")) {
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
