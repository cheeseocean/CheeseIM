package com.cheeseocean.im.business.service.friend;

import com.cheeseocean.im.common.api.event.FriendRelationEvent;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.core.notification.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class FriendRealtimeNotifier {

    private static final Logger log = LoggerFactory.getLogger(FriendRealtimeNotifier.class);

    private final NotificationSender notificationSender;

    public FriendRealtimeNotifier(NotificationSender notificationSender) {
        this.notificationSender = notificationSender;
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

    public void friendDeleted(String userId, String friendUserId) {
        notifyUsers("friend_deleted", userId, friendUserId);
    }

    public void friendRemarkSet(String userId, String friendUserId) {
        notifyUsers("friend_remark_set", userId, friendUserId);
    }

    public void blackAdded(String userId, String targetUserId) {
        notifyUsers("black_added", userId, targetUserId);
    }

    public void blackDeleted(String userId, String targetUserId) {
        notifyUsers("black_deleted", userId, targetUserId);
    }

    public void friendInfoUpdated(String userId, String friendUserId) {
        notifyUsers("friend_info_updated", userId, friendUserId);
    }

    private void notifyUsers(String notificationType, String actorUserId, String targetUserId) {
        if (actorUserId == null || targetUserId == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Set<String> targets = new LinkedHashSet<>();
        targets.add(actorUserId);
        targets.add(targetUserId);
        for (String recipientUserId : targets) {
            FriendRelationEvent event = buildEvent(recipientUserId, notificationType, actorUserId, targetUserId, now);
            try {
                notificationSender.sendToUser(
                        actorUserId,
                        recipientUserId,
                        ContentType.SYSTEM_NOTIFY,
                        notificationType,
                        event,
                        Map.of(
                                "actorUserId", actorUserId,
                                "peerUserId", event.getPeerUserId()
                        )
                );
            } catch (RuntimeException ex) {
                log.warn(
                        "failed to send friend notification, notificationType={}, actorUserId={}, recipientUserId={}",
                        notificationType,
                        actorUserId,
                        recipientUserId,
                        ex
                );
            }
        }
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
