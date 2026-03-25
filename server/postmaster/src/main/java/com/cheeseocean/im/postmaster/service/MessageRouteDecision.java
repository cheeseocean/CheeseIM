package com.cheeseocean.im.postmaster.service;

public record MessageRouteDecision(
        boolean persistHistory,
        boolean updateConversation,
        boolean updateUnread,
        boolean sendDelivery,
        boolean sendOfflineIfFail,
        boolean senderSync,
        boolean notification,
        boolean updateLastMessage) {
}
