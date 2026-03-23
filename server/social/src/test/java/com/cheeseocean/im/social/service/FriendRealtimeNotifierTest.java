package com.cheeseocean.im.social.service;

import com.cheeseocean.im.common.api.event.FriendRelationEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FriendRealtimeNotifierTest {

    @Test
    void friendRequestCreatedShouldPublishRelationEventForBothParticipants() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        FriendRealtimeNotifier notifier = new FriendRealtimeNotifier(kafkaTemplate);

        notifier.friendRequestCreated("userA", "userB");

        var captor = org.mockito.ArgumentCaptor.forClass(FriendRelationEvent.class);
        verify(kafkaTemplate, times(2)).send(eq(TopicNames.FRIEND_RELATION), anyString(), captor.capture());
        assertEquals("userA", captor.getAllValues().get(0).getRecipientUserId());
        assertEquals("friend_request_created", captor.getAllValues().get(0).getEventType());
        assertEquals("userB", captor.getAllValues().get(0).getPeerUserId());
        assertEquals("userB", captor.getAllValues().get(1).getRecipientUserId());
    }
}
