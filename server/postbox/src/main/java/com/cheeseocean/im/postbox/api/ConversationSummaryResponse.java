package com.cheeseocean.im.postbox.api;

public class ConversationSummaryResponse {

    private String conversationId;
    private String title;
    private String subtitle;
    private String kind;
    private String peerUserId;
    private String lastMessagePreview;
    private Long lastMessageTime;
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

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
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

    public Long getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Long lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
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
