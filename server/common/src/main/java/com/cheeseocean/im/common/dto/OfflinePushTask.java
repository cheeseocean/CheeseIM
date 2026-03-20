package com.cheeseocean.im.common.dto;

import java.io.Serializable;

public class OfflinePushTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String traceId;
    private String messageId;
    private String conversationId;
    private Long conversationSeq;
    private String receiverId;
    private String senderId;
    private Integer sessionType;
    private Integer contentType;
    private String content;
    private String attachedInfo;

    public static OfflinePushTask from(DeliveryTaskCommand task) {
        OfflinePushTask pushTask = new OfflinePushTask();
        pushTask.setEventId(task.getEventId());
        pushTask.setTraceId(task.getTraceId());
        pushTask.setMessageId(task.getMessageId());
        pushTask.setConversationId(task.getConversationId());
        pushTask.setConversationSeq(task.getConversationSeq());
        pushTask.setReceiverId(task.getReceiverId());
        pushTask.setSenderId(task.getSenderId());
        pushTask.setSessionType(task.getSessionType());
        pushTask.setContentType(task.getContentType());
        pushTask.setContent(task.getContent());
        pushTask.setAttachedInfo(task.getAttachedInfo());
        return pushTask;
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

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
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
