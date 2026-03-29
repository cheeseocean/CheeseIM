package com.cheeseocean.im.social.service.conversation;

import com.cheeseocean.im.common.api.event.ConversationSettingsEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.annotation.QueueProducer;
import org.springframework.stereotype.Component;

@Component
@QueueProducer
public class ConversationSettingsNotifier {

    private final QueueAdapter queueAdapter;

    public ConversationSettingsNotifier(QueueAdapter queueAdapter) {
        this.queueAdapter = queueAdapter;
    }

    public void notifyRecvMsgOptChanged(String userId, String conversationId, int recvMsgOpt) {
        ConversationSettingsEvent event = new ConversationSettingsEvent();
        event.setRecipientUserId(userId);
        event.setConversationId(conversationId);
        event.setRecvMsgOpt(recvMsgOpt);
        event.setOccurredAt(System.currentTimeMillis());
        queueAdapter.send(TopicNames.CONVERSATION_SETTINGS, userId, event);
    }
}
