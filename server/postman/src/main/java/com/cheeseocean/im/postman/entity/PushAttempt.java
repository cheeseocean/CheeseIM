package com.cheeseocean.im.postman.entity;

import java.time.Instant;

public class PushAttempt {

    private final String serverMsgId;
    private final String userId;
    private final Instant createdAt;
    private boolean cancelled;

    public PushAttempt(String serverMsgId, String userId) {
        this.serverMsgId = serverMsgId;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }
}
