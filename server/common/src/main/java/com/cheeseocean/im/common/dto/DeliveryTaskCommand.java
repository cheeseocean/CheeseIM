package com.cheeseocean.im.common.dto;

import java.io.Serializable;

public class DeliveryTaskCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String traceId;
    private String messageId;
    private String conversationId;
    private Long conversationSeq;
    private String senderId;
    private String receiverId;
    private String deviceId;
    private Integer sessionType;
    private Integer contentType;
    private String content;
    private String attachedInfo;

    public static DeliveryTaskCommand from(HistoryTask task) {
        DeliveryTaskCommand command = new DeliveryTaskCommand();
        command.setEventId(task.getEventId());
        command.setTraceId(task.getTraceId());
        command.setMessageId(task.getMessageId());
        command.setConversationId(task.getConversationId());
        command.setConversationSeq(task.getConversationSeq());
        command.setSenderId(task.getSenderId());
        command.setReceiverId(task.getReceiverId());
        command.setSessionType(task.getSessionType());
        command.setContentType(task.getContentType());
        command.setContent(task.getContent());
        command.setAttachedInfo(task.getAttachedInfo());
        return command;
    }

    public String deliveryKey() {
        if (receiverId != null && !receiverId.isBlank()) {
            return receiverId;
        }
        return conversationId == null ? messageId : conversationId;
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

    public Integer getSessionType() {
        return sessionType;
    }

    public void setSessionType(Integer sessionType) {
        this.sessionType = sessionType;
    }

    public Integer getContentType() {
        return contentType;
    }

    public void setContentType(Integer contentType) {
        this.contentType = contentType;
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
}
