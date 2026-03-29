package com.cheeseocean.im.business.service.user;

import com.cheeseocean.im.common.api.event.UserSettingsEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserSettingsNotifier {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UserSettingsNotifier(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void notifyGlobalRecvMsgOptChanged(String userId, int globalRecvMsgOpt) {
        UserSettingsEvent event = new UserSettingsEvent();
        event.setRecipientUserId(userId);
        event.setGlobalRecvMsgOpt(globalRecvMsgOpt);
        event.setOccurredAt(System.currentTimeMillis());
        kafkaTemplate.send(TopicNames.USER_SETTINGS, userId, event);
    }
}
