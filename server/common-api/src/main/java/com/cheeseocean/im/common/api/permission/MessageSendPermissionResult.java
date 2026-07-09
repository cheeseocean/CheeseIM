package com.cheeseocean.im.common.api.permission;

import lombok.Data;

import java.io.Serializable;

/**
 * 消息发送权限聚合查询结果。
 */
@Data
public class MessageSendPermissionResult implements Serializable {

    private boolean blockedByReceiver;
    private int globalReceiveOption;
    private int conversationReceiveOption;

    public static MessageSendPermissionResult allow(int globalReceiveOption, int conversationReceiveOption) {
        MessageSendPermissionResult result = new MessageSendPermissionResult();
        result.setBlockedByReceiver(false);
        result.setGlobalReceiveOption(globalReceiveOption);
        result.setConversationReceiveOption(conversationReceiveOption);
        return result;
    }

    public static MessageSendPermissionResult blocked(int globalReceiveOption, int conversationReceiveOption) {
        MessageSendPermissionResult result = allow(globalReceiveOption, conversationReceiveOption);
        result.setBlockedByReceiver(true);
        return result;
    }
}
