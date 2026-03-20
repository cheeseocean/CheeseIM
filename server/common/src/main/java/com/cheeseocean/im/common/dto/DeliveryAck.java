package com.cheeseocean.im.common.dto;

import java.io.Serializable;

public class DeliveryAck implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serverMsgId;
    private String conversationId;
    private String userId;
    private String deviceId;
    private String ackType;
    private Long eventTime;
    private Long conversationSeq;

    public static DeliveryAck receive(String serverMsgId, String conversationId, String userId, String deviceId, long eventTime) {
        return build(serverMsgId, conversationId, userId, deviceId, "RECEIVED", eventTime);
    }

    public static DeliveryAck read(String serverMsgId, String conversationId, String userId, String deviceId, long eventTime) {
        return build(serverMsgId, conversationId, userId, deviceId, "READ", eventTime);
    }

    public static DeliveryAck recall(String serverMsgId, String conversationId, String userId, String deviceId, long eventTime) {
        return build(serverMsgId, conversationId, userId, deviceId, "RECALL", eventTime);
    }

    private static DeliveryAck build(String serverMsgId, String conversationId, String userId, String deviceId,
                                     String ackType, long eventTime) {
        DeliveryAck ack = new DeliveryAck();
        ack.setServerMsgId(serverMsgId);
        ack.setConversationId(conversationId);
        ack.setUserId(userId);
        ack.setDeviceId(deviceId);
        ack.setAckType(ackType);
        ack.setEventTime(eventTime);
        return ack;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getAckType() {
        return ackType;
    }

    public void setAckType(String ackType) {
        this.ackType = ackType;
    }

    public Long getEventTime() {
        return eventTime;
    }

    public void setEventTime(Long eventTime) {
        this.eventTime = eventTime;
    }

    public Long getConversationSeq() {
        return conversationSeq;
    }

    public void setConversationSeq(Long conversationSeq) {
        this.conversationSeq = conversationSeq;
    }

    public Long getSeq() {
        return conversationSeq;
    }

    public void setSeq(Long seq) {
        this.conversationSeq = seq;
    }
}
