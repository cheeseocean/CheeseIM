package com.cheeseocean.im.common.api.dto.message;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ChatSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("requestId")
    private String requestId;

    @JsonProperty("senderId")
    private String senderId;

    @JsonProperty("sessionType")
    private Integer sessionType;

    @JsonProperty("recvID")
    @JsonAlias("recvId")
    private String recvId;

    @JsonProperty("groupID")
    @JsonAlias("groupId")
    private String groupId;

    @JsonProperty("clientMsgID")
    @JsonAlias("clientMsgId")
    private String clientMsgId;

    @JsonProperty("contentType")
    private Integer contentType;

    @JsonProperty("content")
    private String content;

    @JsonProperty("sendTime")
    private Long sendTime;

    @JsonProperty("options")
    private MessageOptions options;

    @JsonProperty("ext")
    private Map<String, String> ext = new HashMap<>();

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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

    public String getRecvId() {
        return recvId;
    }

    public void setRecvId(String recvId) {
        this.recvId = recvId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
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

    public Long getSendTime() {
        return sendTime;
    }

    public void setSendTime(Long sendTime) {
        this.sendTime = sendTime;
    }

    public MessageOptions getOptions() {
        return options;
    }

    public void setOptions(MessageOptions options) {
        this.options = options;
    }

    public Map<String, String> getExt() {
        return ext;
    }

    public void setExt(Map<String, String> ext) {
        this.ext = ext == null ? new HashMap<>() : new HashMap<>(ext);
    }
}
