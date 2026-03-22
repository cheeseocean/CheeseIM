package com.cheeseocean.im.postbox.api;

import com.cheeseocean.im.common.core.enums.ConversationKind;
import com.cheeseocean.im.common.core.enums.MessagePreviewType;

public class ConversationSummaryResponse {

    private String conversationId;
    private String title;
    private String subtitle;
    private ConversationKind kind;
    private String peerUserId;
    private String lastMessagePreview;
    private MessagePreviewType lastMessagePreviewType;
    private Long lastMessageTime;
    private boolean notification;
    private int unreadCount;
    private String accentColor;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public ConversationKind getKind() {
        return kind;
    }

    public void setKind(ConversationKind kind) {
        this.kind = kind;
    }

    public String getPeerUserId() {
        return peerUserId;
    }

    public void setPeerUserId(String peerUserId) {
        this.peerUserId = peerUserId;
    }

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }

    public void setLastMessagePreview(String lastMessagePreview) {
        this.lastMessagePreview = lastMessagePreview;
    }

    public MessagePreviewType getLastMessagePreviewType() {
        return lastMessagePreviewType;
    }

    public void setLastMessagePreviewType(MessagePreviewType lastMessagePreviewType) {
        this.lastMessagePreviewType = lastMessagePreviewType;
    }

    public Long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public boolean isNotification() {
        return notification;
    }

    public void setNotification(boolean notification) {
        this.notification = notification;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }
}
