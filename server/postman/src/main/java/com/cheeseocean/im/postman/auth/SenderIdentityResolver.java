package com.cheeseocean.im.postman.auth;

import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.postman.model.SendMessageCommand;
import org.springframework.stereotype.Component;

@Component
public class SenderIdentityResolver {

    public SendMessageCommand resolve(DeliveryCommand command) {
        SendMessageCommand sendMessageCommand = new SendMessageCommand();
        sendMessageCommand.setConversationId(command.getConversationId());
        sendMessageCommand.setSenderUserId(command.getSenderId());
        sendMessageCommand.setSenderDeviceId(command.getDeviceId());
        sendMessageCommand.setClientMsgId(command.getClientMsgId());
        sendMessageCommand.setMessageType(command.getContentType());
        sendMessageCommand.setBody(command.getContent());
        return sendMessageCommand;
    }
}
