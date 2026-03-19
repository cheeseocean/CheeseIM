package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.common.utils.ConversationIds;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.springframework.stereotype.Component;

@Component
public class MessageProtoMapper {

    public DeliveryCommand toDeliveryCommand(Message message, UserConnection connection) {
        ConnectionContext context = connection.getContext();
        String senderId = context != null && context.getUserId() != null ? context.getUserId() : connection.getUserID();
        String deviceId = context != null && context.getDeviceId() != null ? context.getDeviceId() : String.valueOf(connection.getPlatformID());
        String conversationId = buildConversationId(message, senderId);
        return DeliveryCommand.builder()
                .clientMsgId(message.getClientMsgID())
                .conversationId(conversationId)
                .senderId(senderId)
                .receiverId(message.getRecvID())
                .deviceId(deviceId)
                .content(message.getContent())
                .contentType(message.getContentType())
                .sessionType(message.getSessionType())
                .attachedInfo(message.getAttachedInfo())
                .build();
    }

    private String buildConversationId(Message message, String senderId) {
        if (message.getSessionType() != null && message.getSessionType() == 2) {
            return "group:" + message.getGroupID();
        }
        return ConversationIds.direct(senderId, message.getRecvID());
    }
}
