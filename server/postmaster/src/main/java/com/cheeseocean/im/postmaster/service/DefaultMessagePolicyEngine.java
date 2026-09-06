package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import org.springframework.stereotype.Component;

/** 根据消息选项决定 ingress 的持久化、投递及通知分流。 */
@Component
public class DefaultMessagePolicyEngine implements MessagePolicyEngine {

    @Override
    public MessageRouteDecision decide(Message event) {
        MessageOptions options = event == null || event.getOptions() == null ? new MessageOptions() : event.getOptions();
        return new MessageRouteDecision(
                !Boolean.FALSE.equals(options.getNeedHistory()),
                !Boolean.FALSE.equals(options.getNeedOnlinePush()),
                Boolean.TRUE.equals(options.getNotification()));
    }
}
