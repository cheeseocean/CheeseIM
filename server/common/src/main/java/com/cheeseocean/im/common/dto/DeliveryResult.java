package com.cheeseocean.im.common.dto;

import com.cheeseocean.im.common.entity.DeliveryState;

import java.io.Serializable;

public class DeliveryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String status;
    private boolean receiverOnline;
    private String serverMsgId;
    private Long storedMessageId;
    private Long conversationSeq;
    private DeliveryState state;

    public static DeliveryResult accepted(String serverMsgId, long conversationSeq) {
        DeliveryResult result = new DeliveryResult();
        result.success = true;
        result.status = "ACCEPTED";
        result.receiverOnline = false;
        result.serverMsgId = serverMsgId;
        result.conversationSeq = conversationSeq;
        result.state = DeliveryState.INIT;
        return result;
    }

    public static DeliveryResult onlineSuccess(String serverMsgId) {
        DeliveryResult result = new DeliveryResult();
        result.success = true;
        result.status = "ONLINE_CONFIRMED";
        result.receiverOnline = true;
        result.serverMsgId = serverMsgId;
        result.state = DeliveryState.ONLINE_CONFIRMED;
        return result;
    }

    public static DeliveryResult failed(String status) {
        DeliveryResult result = new DeliveryResult();
        result.success = false;
        result.status = status;
        result.receiverOnline = false;
        result.state = DeliveryState.FAILED_FINAL;
        return result;
    }

    public static DeliveryResult acceptedAck(String serverMsgId) {
        DeliveryResult result = new DeliveryResult();
        result.success = true;
        result.status = "ACK_ACCEPTED";
        result.receiverOnline = false;
        result.serverMsgId = serverMsgId;
        result.state = DeliveryState.PERSISTED;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isReceiverOnline() {
        return receiverOnline;
    }

    public void setReceiverOnline(boolean receiverOnline) {
        this.receiverOnline = receiverOnline;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public Long getStoredMessageId() {
        return storedMessageId;
    }

    public void setStoredMessageId(Long storedMessageId) {
        this.storedMessageId = storedMessageId;
    }

    public Long getConversationSeq() {
        return conversationSeq;
    }

    public void setConversationSeq(Long conversationSeq) {
        this.conversationSeq = conversationSeq;
    }

    public DeliveryState getState() {
        return state;
    }

    public void setState(DeliveryState state) {
        this.state = state;
    }
}
