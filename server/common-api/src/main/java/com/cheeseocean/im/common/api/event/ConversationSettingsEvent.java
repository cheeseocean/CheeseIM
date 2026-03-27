package com.cheeseocean.im.common.api.event;

import java.io.Serializable;

/**
 * Published when a user changes per-conversation settings (e.g. recvMsgOpt).
 * Consumed by postman to push the change to all devices of the same user
 * (multi-device sync).
 */
public class ConversationSettingsEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String recipientUserId;
    private String conversationId;
    private int    recvMsgOpt;
    private long   occurredAt;

    public String getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(String recipientUserId) { this.recipientUserId = recipientUserId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public int getRecvMsgOpt() { return recvMsgOpt; }
    public void setRecvMsgOpt(int recvMsgOpt) { this.recvMsgOpt = recvMsgOpt; }

    public long getOccurredAt() { return occurredAt; }
    public void setOccurredAt(long occurredAt) { this.occurredAt = occurredAt; }
}
