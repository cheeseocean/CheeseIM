package com.cheeseocean.im.common.api.event;

import com.cheeseocean.im.common.api.enums.OfflinePushTriggerReason;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class OfflinePushEvent implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final String TRIGGER_REASON_ATTRIBUTE = "_cheeseim_offline_trigger";

    private String              userId;
    private String              conversationId;
    private Long                seq;
    private String              serverMsgId;
    private String              senderId;
    private Integer             sessionType;
    private Integer             contentType;
    private boolean             notification;
    private String              title;
    private String              content;
    private Map<String, String> attributes = new HashMap<>();

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

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
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

    public boolean isNotification() {
        return notification;
    }

    public void setNotification(boolean notification) {
        this.notification = notification;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes == null ? new HashMap<>() : new HashMap<>(attributes);
    }

    /**
     * 通过既有 attributes wire 字段携带内部触发原因，避免为内部队列语义修改客户端协议。
     */
    public void setTriggerReason(OfflinePushTriggerReason reason) {
        if (reason == null) {
            attributes.remove(TRIGGER_REASON_ATTRIBUTE);
        } else {
            attributes.put(TRIGGER_REASON_ATTRIBUTE, reason.getCode());
        }
    }

    public OfflinePushTriggerReason getTriggerReason() {
        return OfflinePushTriggerReason.fromCode(attributes.get(TRIGGER_REASON_ATTRIBUTE));
    }
}
