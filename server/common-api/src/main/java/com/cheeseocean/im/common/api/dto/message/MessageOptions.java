package com.cheeseocean.im.common.api.dto.message;

import java.io.Serializable;

public class MessageOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean needHistory;
    private Boolean needConversation;
    private Boolean needUnreadCount;
    private Boolean needOnlinePush;
    private Boolean needOfflinePush;
    private Boolean senderSync;
    private Boolean notification;
    private Boolean needLastMessage;

    public Boolean isNeedHistory() {
        return needHistory;
    }

    public void setNeedHistory(Boolean needHistory) {
        this.needHistory = needHistory;
    }

    public Boolean isNeedConversation() {
        return needConversation;
    }

    public void setNeedConversation(Boolean needConversation) {
        this.needConversation = needConversation;
    }

    public Boolean isNeedUnreadCount() {
        return needUnreadCount;
    }

    public void setNeedUnreadCount(Boolean needUnreadCount) {
        this.needUnreadCount = needUnreadCount;
    }

    public Boolean isNeedOnlinePush() {
        return needOnlinePush;
    }

    public void setNeedOnlinePush(Boolean needOnlinePush) {
        this.needOnlinePush = needOnlinePush;
    }

    public Boolean isNeedOfflinePush() {
        return needOfflinePush;
    }

    public void setNeedOfflinePush(Boolean needOfflinePush) {
        this.needOfflinePush = needOfflinePush;
    }

    public Boolean isSenderSync() {
        return senderSync;
    }

    public void setSenderSync(Boolean senderSync) {
        this.senderSync = senderSync;
    }

    public Boolean isNotification() {
        return notification;
    }

    public void setNotification(Boolean notification) {
        this.notification = notification;
    }

    public Boolean isNeedLastMessage() {
        return needLastMessage;
    }

    public void setNeedLastMessage(Boolean needLastMessage) {
        this.needLastMessage = needLastMessage;
    }
}
