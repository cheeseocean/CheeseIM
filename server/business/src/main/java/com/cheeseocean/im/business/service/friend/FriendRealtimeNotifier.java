package com.cheeseocean.im.business.service.friend;

import com.cheeseocean.im.common.api.event.FriendRelationEvent;
import com.cheeseocean.im.common.core.constants.TopicNames;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class FriendRealtimeNotifier {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FriendRealtimeNotifier(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void friendRequestCreated(String fromUserId, String toUserId) {
        notifyUsers("friend_request_created", fromUserId, toUserId);
    }

    public void friendRequestAccepted(String userId, String friendUserId) {
        notifyUsers("friend_request_accepted", userId, friendUserId);
    }

    public void friendRequestRejected(String userId, String friendUserId) {
        notifyUsers("friend_request_rejected", userId, friendUserId);
    }

    public void friendRequestCancelled(String userId, String friendUserId) {
        notifyUsers("friend_request_cancelled", userId, friendUserId);
    }

    private void notifyUsers(String notificationType, String actorUserId, String targetUserId) {
        if (actorUserId == null || targetUserId == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Set<String> targets = new LinkedHashSet<>();
        targets.add(actorUserId);
        targets.add(targetUserId);

    }

    private FriendRelationEvent buildEvent(String recipientUserId,
                                           String notificationType,
                                           String actorUserId,
                                           String targetUserId,
                                           long occurredAt) {
        FriendRelationEvent event = new FriendRelationEvent();
        event.setRecipientUserId(recipientUserId);
        event.setActorUserId(actorUserId);
        event.setPeerUserId(recipientUserId.equals(actorUserId) ? targetUserId : actorUserId);
        event.setEventType(notificationType);
        event.setOccurredAt(occurredAt);
        return event;
    }
}
