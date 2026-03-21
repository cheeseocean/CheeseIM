package com.cheeseocean.im.common.dto;

import java.io.Serializable;
import java.util.List;

public class DeliveryCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String clientMsgId;
    private final String conversationId;
    private final String senderId;
    private final String receiverId;
    private final String deviceId;
    private final String content;
    private final Integer contentType;
    private final Integer sessionType;
    private final String attachedInfo;
    private final List<String> targetUserIds;

    private DeliveryCommand(Builder builder) {
        this.clientMsgId = required(builder.clientMsgId, "clientMsgId");
        this.conversationId = required(builder.conversationId, "conversationId");
        this.senderId = required(builder.senderId, "senderId");
        this.receiverId = builder.receiverId;
        this.deviceId = builder.deviceId;
        this.content = builder.content;
        this.contentType = builder.contentType;
        this.sessionType = builder.sessionType;
        this.attachedInfo = builder.attachedInfo;
        this.targetUserIds = builder.targetUserIds == null ? List.of() : List.copyOf(builder.targetUserIds);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getContent() {
        return content;
    }

    public Integer getContentType() {
        return contentType;
    }

    public Integer getSessionType() {
        return sessionType;
    }

    public String getAttachedInfo() {
        return attachedInfo;
    }

    public List<String> getTargetUserIds() {
        return targetUserIds;
    }

    public boolean isGroupDelivery() {
        return sessionType != null && sessionType == 2;
    }

    public boolean isSingleDelivery() {
        return !isGroupDelivery();
    }

    public static final class Builder {

        private String clientMsgId;
        private String conversationId;
        private String senderId;
        private String receiverId;
        private String deviceId;
        private String content;
        private Integer contentType;
        private Integer sessionType;
        private String attachedInfo;
        private List<String> targetUserIds;

        public Builder clientMsgId(String clientMsgId) {
            this.clientMsgId = clientMsgId;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder senderId(String senderId) {
            this.senderId = senderId;
            return this;
        }

        public Builder receiverId(String receiverId) {
            this.receiverId = receiverId;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder contentType(Integer contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder sessionType(Integer sessionType) {
            this.sessionType = sessionType;
            return this;
        }

        public Builder attachedInfo(String attachedInfo) {
            this.attachedInfo = attachedInfo;
            return this;
        }

        public Builder targetUserIds(List<String> targetUserIds) {
            this.targetUserIds = targetUserIds;
            return this;
        }

        public DeliveryCommand build() {
            return new DeliveryCommand(this);
        }
    }
}
