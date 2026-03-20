package com.cheeseocean.im.common.dto;

import java.io.Serializable;
import java.util.List;

public class HistoryTask implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String traceId;
    private String messageId;
    private String clientMsgId;
    private String conversationId;
    private Long conversationSeq;
    private String senderId;
    private String receiverId;
    private Integer sessionType;
    private Integer contentType;
    private String content;
    private String attachedInfo;
    private List<String> targetUserIds = List.of();

    public static HistoryTask single(IngressEvent event) {
        HistoryTask task = new HistoryTask();
        task.copyFrom(event);
        return task;
    }

    public static HistoryTask groupBatch(IngressEvent event, List<String> receiverIds) {
        HistoryTask task = new HistoryTask();
        task.copyFrom(event);
        task.setTargetUserIds(receiverIds);
        return task;
    }

    public static HistoryTask singleFromProto(MessageProto message) {
        HistoryTask task = new HistoryTask();
        task.setEventId(message.getServerMsgId());
        task.setMessageId(message.getServerMsgId());
        task.setClientMsgId(message.getClientMsgId());
        task.setConversationId(message.getConversationId());
        task.setConversationSeq(message.getConversationSeq());
        task.setSenderId(message.getSenderId());
        task.setReceiverId(message.getReceiverId());
        task.setSessionType(message.getSessionType());
        task.setContentType(message.getContentType());
        task.setContent(message.getContent());
        task.setAttachedInfo(message.getAttachedInfo());
        return task;
    }

    public String deliveryKey() {
        if (receiverId != null && !receiverId.isBlank()) {
            return receiverId;
        }
        return conversationId == null ? messageId : conversationId;
    }

    private void copyFrom(IngressEvent event) {
        setEventId(event.getEventId());
        setTraceId(event.getTraceId());
        setMessageId(event.getMessageId());
        setClientMsgId(event.getClientMsgId());
        setConversationId(event.getConversationId());
        setConversationSeq(event.getConversationSeq());
        setSenderId(event.getSenderId());
        setReceiverId(event.getReceiverId());
        setSessionType(event.getSessionType());
        setContentType(event.getContentType());
        setContent(event.getContent());
        setAttachedInfo(event.getAttachedInfo());
        setTargetUserIds(event.getTargetUserIds());
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

    public List<String> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(List<String> targetUserIds) {
        this.targetUserIds = targetUserIds == null ? List.of() : List.copyOf(targetUserIds);
    }
}
