package com.cheeseocean.im.common.api.dto.receipt;

import com.cheeseocean.im.common.core.enums.ReceiptType;

import java.io.Serializable;

public class ReceiptAckReq implements Serializable {

    private static final long serialVersionUID = 1L;

    private ReceiptType ackType;
    private String userId;
    private String conversationId;
    private String serverMsgId;
    private String deviceId;
    private Long seq;
    private Long eventTime;

    public ReceiptType getAckType() {
        return ackType;
    }

    public void setAckType(ReceiptType ackType) {
        this.ackType = ackType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public Long getEventTime() {
        return eventTime;
    }

    public void setEventTime(Long eventTime) {
        this.eventTime = eventTime;
    }
}
