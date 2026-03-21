package com.cheeseocean.im.common.dto;

import java.io.Serializable;
import java.util.List;

public class IngressEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String traceId;
    private String messageId;
    private String clientMsgId;
    private String conversationId;
    private Long conversationSeq;
    private String senderId;
    private String receiverId;
    private String deviceId;
    private Integer contentType;
    private Integer sessionType;
    private String content;
    private String attachedInfo;
    private List<String> targetUserIds = List.of();
    private Long acceptedAt;

    public static IngressEvent from(DeliveryCommand command, String messageId, long conversationSeq, String traceId) {
        IngressEvent event = new IngressEvent();
        event.setEventId(messageId);
        event.setTraceId(traceId);
        event.setMessageId(messageId);
        event.setClientMsgId(command.getClientMsgId());
        event.setConversationId(command.getConversationId());
        event.setConversationSeq(conversationSeq);
        event.setSenderId(command.getSenderId());
        event.setReceiverId(command.getReceiverId());
        event.setDeviceId(command.getDeviceId());
        event.setContentType(command.getContentType());
        event.setSessionType(command.getSessionType());
        event.setContent(command.getContent());
        event.setAttachedInfo(command.getAttachedInfo());
        event.setTargetUserIds(command.getTargetUserIds());
        event.setAcceptedAt(System.currentTimeMillis());
        return event;
    }

    public boolean isGroupDelivery() {
        return sessionType != null && sessionType == 2;
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

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Long getConversationSeq() {
        return conversationSeq;
    }

    public void setConversationSeq(Long conversationSeq) {
        this.conversationSeq = conversationSeq;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Integer getContentType() {
        return contentType;
    }

    public void setContentType(Integer contentType) {
        this.contentType = contentType;
    }

    public Integer getSessionType() {
        return sessionType;
    }

    public void setSessionType(Integer sessionType) {
        this.sessionType = sessionType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAttachedInfo() {
        return attachedInfo;
    }

    public void setAttachedInfo(String attachedInfo) {
        this.attachedInfo = attachedInfo;
    }

    public List<String> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(List<String> targetUserIds) {
        this.targetUserIds = targetUserIds == null ? List.of() : List.copyOf(targetUserIds);
    }

    public Long getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Long acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}
