package com.cheeseocean.im.common.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;

public class DeliveryTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String serverMsgId;
    private final String receiverId;
    private final String deviceId;
    private DeliveryState state;
    private Instant updatedAt;
    private int retryCount;
    private Instant nextRetryAt;

    @JsonCreator
    private DeliveryTask(@JsonProperty("serverMsgId") String serverMsgId,
                         @JsonProperty("receiverId") String receiverId,
                         @JsonProperty("deviceId") String deviceId,
                         @JsonProperty("state") DeliveryState state,
                         @JsonProperty("retryCount") int retryCount,
                         @JsonProperty("nextRetryAt") Instant nextRetryAt) {
        this.serverMsgId = serverMsgId;
        this.receiverId = receiverId;
        this.deviceId = deviceId;
        this.state = state;
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
        this.updatedAt = Instant.now();
    }

    public static DeliveryTask newTask(String serverMsgId, String receiverId, String deviceId) {
        return new DeliveryTask(serverMsgId, receiverId, deviceId, DeliveryState.INIT, 0, null);
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public DeliveryState getState() {
        return state;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
        this.updatedAt = Instant.now();
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
        this.updatedAt = Instant.now();
    }

    public void moveTo(DeliveryState nextState) {
        this.state = nextState;
        this.updatedAt = Instant.now();
    }

    public void markRetryScheduled(int retryCount, Instant nextRetryAt) {
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
        this.state = DeliveryState.FAILED_RECOVERABLE;
        this.updatedAt = Instant.now();
    }
}
