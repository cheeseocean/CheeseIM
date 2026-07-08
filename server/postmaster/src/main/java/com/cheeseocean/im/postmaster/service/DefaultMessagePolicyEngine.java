package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import org.springframework.stereotype.Component;

@Component
public class DefaultMessagePolicyEngine implements MessagePolicyEngine {

    @Override
    public MessageRouteDecision decide(Message event) {
        MessageOptions options = event == null || event.getOptions() == null ? new MessageOptions() : event.getOptions();
        return new MessageRouteDecision(
                !Boolean.FALSE.equals(options.getNeedHistory()),
                !Boolean.FALSE.equals(options.getNeedConversation()),
                !Boolean.FALSE.equals(options.getNeedUnreadCount()),
                !Boolean.FALSE.equals(options.getNeedOnlinePush()),
                !Boolean.FALSE.equals(options.getNeedOfflinePush()),
                Boolean.TRUE.equals(options.getSenderSync()),
                Boolean.TRUE.equals(options.getNotification()),
                !Boolean.FALSE.equals(options.getNeedLastMessage()));
    }
}
