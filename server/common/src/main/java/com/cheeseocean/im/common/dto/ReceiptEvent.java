package com.cheeseocean.im.common.dto;

import java.io.Serializable;

public class ReceiptEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String traceId;
    private String receiptType;
    private String userId;
    private String conversationId;
    private String serverMsgId;
    private String deviceId;
    private Long seq;
    private Long receiptTime;

    public static ReceiptEvent delivered(String userId, String conversationId, String serverMsgId, Long seq, String deviceId) {
        return create("DELIVERED", userId, conversationId, serverMsgId, seq, deviceId);
    }

    public static ReceiptEvent readCursor(String userId, String conversationId, Long seq, String deviceId) {
        return create("READ_CURSOR", userId, conversationId, null, seq, deviceId);
    }

    public static ReceiptEvent fromLegacyAck(DeliveryAck ack) {
        if ("READ".equals(ack.getAckType())) {
            return readCursorFromLegacyAck(ack);
        }
        return create(ack.getAckType(), ack.getUserId(), ack.getConversationId(), ack.getServerMsgId(), ack.getSeq(), ack.getDeviceId(), ack.getEventTime());
    }

    public static ReceiptEvent readCursorFromLegacyAck(DeliveryAck ack) {
        return create("READ_CURSOR", ack.getUserId(), ack.getConversationId(), null, ack.getSeq(), ack.getDeviceId(), ack.getEventTime());
    }

    private static ReceiptEvent create(String receiptType, String userId, String conversationId, String serverMsgId, Long seq, String deviceId) {
        return create(receiptType, userId, conversationId, serverMsgId, seq, deviceId, System.currentTimeMillis());
    }

    private static ReceiptEvent create(String receiptType, String userId, String conversationId, String serverMsgId,
                                       Long seq, String deviceId, Long receiptTime) {
        ReceiptEvent event = new ReceiptEvent();
        event.setReceiptType(receiptType);
        event.setUserId(userId);
        event.setConversationId(conversationId);
        event.setServerMsgId(serverMsgId);
        event.setSeq(seq);
        event.setDeviceId(deviceId);
        event.setReceiptTime(receiptTime);
        String identity = "READ_CURSOR".equals(receiptType) ? conversationId : (serverMsgId == null ? conversationId : serverMsgId);
        event.setEventId(identity + ":" + receiptType + ":" + receiptTime);
        return event;
    }

    public boolean isDelivered() {
        return "DELIVERED".equals(receiptType) || "RECEIVED".equals(receiptType);
    }

    public boolean isReadCursor() {
        return "READ_CURSOR".equals(receiptType) || "READ".equals(receiptType);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getReceiptType() {
        return receiptType;
    }

    public void setReceiptType(String receiptType) {
        this.receiptType = receiptType;
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

    public Long getReceiptTime() {
        return receiptTime;
    }

    public void setReceiptTime(Long receiptTime) {
        this.receiptTime = receiptTime;
    }
}
