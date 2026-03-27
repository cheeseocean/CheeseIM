package com.cheeseocean.im.common.api.event;

import java.io.Serializable;

/**
 * Published when a user changes global settings (e.g. globalRecvMsgOpt).
 * Consumed by postman to push the change to all other devices of the same user
 * (multi-device sync).
 */
public class UserSettingsEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String recipientUserId;
    private int    globalRecvMsgOpt;
    private long   occurredAt;

    public String getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(String recipientUserId) { this.recipientUserId = recipientUserId; }

    public int getGlobalRecvMsgOpt() { return globalRecvMsgOpt; }
    public void setGlobalRecvMsgOpt(int globalRecvMsgOpt) { this.globalRecvMsgOpt = globalRecvMsgOpt; }

    public long getOccurredAt() { return occurredAt; }
    public void setOccurredAt(long occurredAt) { this.occurredAt = occurredAt; }
}
