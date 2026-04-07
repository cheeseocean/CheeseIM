package com.cheeseocean.im.common.api.dto.message;

import lombok.Data;

import java.io.Serializable;

@Data
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

}