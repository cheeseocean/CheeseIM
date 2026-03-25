package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.event.IngressEvent;
import org.springframework.stereotype.Component;

@Component
public class DefaultMessagePolicyEngine implements MessagePolicyEngine {

    @Override
    public MessageRouteDecision decide(IngressEvent event) {
        MessageOptions options = event == null || event.getOptions() == null ? new MessageOptions() : event.getOptions();
        return new MessageRouteDecision(
                !Boolean.FALSE.equals(options.isNeedHistory()),
                !Boolean.FALSE.equals(options.isNeedConversation()),
                !Boolean.FALSE.equals(options.isNeedUnreadCount()),
                !Boolean.FALSE.equals(options.isNeedOnlinePush()),
                !Boolean.FALSE.equals(options.isNeedOfflinePush()),
                Boolean.TRUE.equals(options.isSenderSync()),
                Boolean.TRUE.equals(options.isNotification()),
                !Boolean.FALSE.equals(options.isNeedLastMessage()));
    }
}
