package com.cheeseocean.im.common.api.event;

import java.io.Serializable;

public class FriendRelationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String recipientUserId;
    private String actorUserId;
    private String peerUserId;
    private String eventType;
    private Long occurredAt;

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(String recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(String actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getPeerUserId() {
        return peerUserId;
    }

    public void setPeerUserId(String peerUserId) {
        this.peerUserId = peerUserId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Long occurredAt) {
        this.occurredAt = occurredAt;
    }
}
