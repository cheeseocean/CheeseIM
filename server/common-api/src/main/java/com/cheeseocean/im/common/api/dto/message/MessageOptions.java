package com.cheeseocean.im.common.api.dto.message;

import java.io.Serializable;

public class MessageOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean needHistory;
    private boolean needConversation;
    private boolean needUnreadCount;
    private boolean needOnlinePush;
    private boolean needOfflinePush;
    private boolean senderSync;
    private boolean notification;
    private boolean needLastMessage;

    public boolean isNeedHistory() {
        return needHistory;
    }

    public void setNeedHistory(boolean needHistory) {
        this.needHistory = needHistory;
    }

    public boolean isNeedConversation() {
        return needConversation;
    }

    public void setNeedConversation(boolean needConversation) {
        this.needConversation = needConversation;
    }

    public boolean isNeedUnreadCount() {
        return needUnreadCount;
    }

    public void setNeedUnreadCount(boolean needUnreadCount) {
        this.needUnreadCount = needUnreadCount;
    }

    public boolean isNeedOnlinePush() {
        return needOnlinePush;
    }

    public void setNeedOnlinePush(boolean needOnlinePush) {
        this.needOnlinePush = needOnlinePush;
    }

    public boolean isNeedOfflinePush() {
        return needOfflinePush;
    }

    public void setNeedOfflinePush(boolean needOfflinePush) {
        this.needOfflinePush = needOfflinePush;
    }

    public boolean isSenderSync() {
        return senderSync;
    }

    public void setSenderSync(boolean senderSync) {
        this.senderSync = senderSync;
    }

    public boolean isNotification() {
        return notification;
    }

    public void setNotification(boolean notification) {
        this.notification = notification;
    }

    public boolean isNeedLastMessage() {
        return needLastMessage;
    }

    public void setNeedLastMessage(boolean needLastMessage) {
        this.needLastMessage = needLastMessage;
    }
}
