package com.cheeseocean.im.business.service.friend;

import com.cheeseocean.im.common.api.event.FriendRelationEvent;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.core.notification.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
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

    public void friendRequestCreated(String fromUserId, String toUserId, String remark) {
        notifyUsers(ContentType.FRIEND_REQUEST, fromUserId, toUserId, remark);
    }

    public void friendRequestAccepted(String userId, String friendUserId) {
        notifyUsers(ContentType.FRIEND_REQUEST_ACCEPTED, userId, friendUserId);
    }

    public void friendRequestRejected(String userId, String friendUserId) {
        notifyUsers(ContentType.FRIEND_REQUEST_REJECTED, userId, friendUserId);
    }

    public void friendRequestCancelled(String userId, String friendUserId) {
        notifyUsers(ContentType.FRIEND_REQUEST_CANCELLED, userId, friendUserId);
    }

    public void friendDeleted(String userId, String friendUserId) {
        notifyUsers(ContentType.FRIEND_DELETED, userId, friendUserId);
    }

    public void friendRemarkModified(String userId, String friendUserId) {
        notifyUsers(ContentType.FRIEND_REMARK_MODIFIED, userId, friendUserId);
    }

    public void blackAdded(String userId, String targetUserId) {
        notifyUsers(ContentType.ADDED_TO_BLACKLIST, userId, targetUserId);
    }

    public void blackDeleted(String userId, String targetUserId) {
        notifyUsers(ContentType.REMOVED_FROM_BLACKLIST, userId, targetUserId);
    }

    public void friendInfoUpdated(String userId, String friendUserId) {
        notifyUsers(ContentType.FRIEND_INFO_UPDATED, userId, friendUserId);
    }

    private void notifyUsers(ContentType contentType, String actorUserId, String targetUserId) {
        notifyUsers(contentType, actorUserId, targetUserId, null);
    }

    private void notifyUsers(ContentType contentType, String actorUserId, String targetUserId, String remark) {
        if (actorUserId == null || targetUserId == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Set<String> targets = new LinkedHashSet<>();
        targets.add(actorUserId);
        targets.add(targetUserId);
        for (String recipientUserId : targets) {
            FriendRelationEvent event = buildEvent(recipientUserId, actorUserId, targetUserId, now);
            try {
                Map<String, String> attributes = new LinkedHashMap<>();
                attributes.put("actorUserId", actorUserId);
                attributes.put("peerUserId", event.getPeerUserId());
                notificationSender.sendToUser(
                        actorUserId,
                        recipientUserId,
                        contentType,
                        event,
                        attributes
                );
            } catch (RuntimeException ex) {
                log.warn(
                        "failed to send friend notification, notificationType={}, actorUserId={}, recipientUserId={}",
                        contentType,
                        actorUserId,
                        recipientUserId,
                        ex
                );
            }
        }
    }

    private FriendRelationEvent buildEvent(String recipientUserId,
                                           String actorUserId,
                                           String targetUserId,
                                           long occurredAt) {
        FriendRelationEvent event = new FriendRelationEvent();
        event.setRecipientUserId(recipientUserId);
        event.setActorUserId(actorUserId);
        event.setPeerUserId(recipientUserId.equals(actorUserId) ? targetUserId : actorUserId);
        event.setOccurredAt(occurredAt);
        return event;
    }
}
