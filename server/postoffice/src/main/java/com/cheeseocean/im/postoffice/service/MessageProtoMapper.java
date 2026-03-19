package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.springframework.stereotype.Component;

@Component
public class MessageProtoMapper {

    public DeliveryCommand toDeliveryCommand(Message message, UserConnection connection) {
        String conversationId = buildConversationId(message, connection.getUserID());
        return DeliveryCommand.builder()
                .clientMsgId(message.getClientMsgID())
                .conversationId(conversationId)
                .senderId(connection.getUserID())
                .receiverId(message.getRecvID())
                .deviceId(String.valueOf(connection.getPlatformID()))
                .content(message.getContent())
                .contentType(message.getContentType())
                .sessionType(message.getSessionType())
                .build();
    }

    private String buildConversationId(Message message, String senderId) {
        if (message.getSessionType() != null && message.getSessionType() == 2) {
            return "group:" + message.getGroupID();
        }
        return "single:" + senderId + ":" + message.getRecvID();
    }
}
