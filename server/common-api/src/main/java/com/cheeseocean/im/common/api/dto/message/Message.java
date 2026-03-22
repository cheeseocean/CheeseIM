package com.cheeseocean.im.common.api.dto.message;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Map;

public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("clientMsgID")
    private String clientMsgID;

    @JsonProperty("serverMsgID")
    private String serverMsgID;

    @JsonProperty("sendID")
    private String sendID;

    @JsonProperty("recvID")
    private String recvID;

    @JsonProperty("groupID")
    private String groupID;

    @JsonProperty("content")
    private String content;

    @JsonProperty("contentType")
    private Integer contentType;

    @JsonProperty("sessionType")
    private Integer sessionType;

    @JsonProperty("sendTime")
    private Long sendTime;

    @JsonProperty("createTime")
    private Long createTime;

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("seq")
    private Long seq;

    @JsonProperty("isRead")
    private Boolean isRead;

    @JsonProperty("platformID")
    private Integer platformID;

    @JsonProperty("ex")
    private String ex;

    @JsonProperty("senderNickname")
    private String senderNickname;

    @JsonProperty("senderFaceURL")
    private String senderFaceURL;

    @JsonProperty("recvNickname")
    private String recvNickname;

    @JsonProperty("recvFaceURL")
    private String recvFaceURL;

    @JsonProperty("options")
    private Map<String, Boolean> options;

    @JsonProperty("attachedInfo")
    private String attachedInfo;

    @JsonProperty("offlinePushInfo")
    private OfflinePushInfo offlinePushInfo;

    @JsonProperty("uniqueID")
    private String uniqueID;

    @JsonProperty("senderPlatformID")
    private Integer senderPlatformID;

    @JsonProperty("recvPlatformID")
    private Integer recvPlatformID;

    @JsonProperty("msgFrom")
    private Integer msgFrom;

    @JsonProperty("subType")
    private Integer subType;

    public Message() {
        this.createTime = System.currentTimeMillis();
        this.sendTime = System.currentTimeMillis();
        this.status = 1;
        this.isRead = false;
    }

    public String getClientMsgID() {
        return clientMsgID;
    }

    public void setClientMsgID(String clientMsgID) {
        this.clientMsgID = clientMsgID;
    }

    public String getServerMsgID() {
        return serverMsgID;
    }

    public void setServerMsgID(String serverMsgID) {
        this.serverMsgID = serverMsgID;
    }

    public String getSendID() {
        return sendID;
    }

    public void setSendID(String sendID) {
        this.sendID = sendID;
    }

    public String getRecvID() {
        return recvID;
    }

    public void setRecvID(String recvID) {
        this.recvID = recvID;
    }

    public String getGroupID() {
        return groupID;
    }

    public void setGroupID(String groupID) {
        this.groupID = groupID;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public Long getSendTime() {
        return sendTime;
    }

    public void setSendTime(Long sendTime) {
        this.sendTime = sendTime;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public Integer getPlatformID() {
        return platformID;
    }

    public void setPlatformID(Integer platformID) {
        this.platformID = platformID;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }

    public String getSenderNickname() {
        return senderNickname;
    }

    public void setSenderNickname(String senderNickname) {
        this.senderNickname = senderNickname;
    }

    public String getSenderFaceURL() {
        return senderFaceURL;
    }

    public void setSenderFaceURL(String senderFaceURL) {
        this.senderFaceURL = senderFaceURL;
    }

    public String getRecvNickname() {
        return recvNickname;
    }

    public void setRecvNickname(String recvNickname) {
        this.recvNickname = recvNickname;
    }

    public String getRecvFaceURL() {
        return recvFaceURL;
    }

    public void setRecvFaceURL(String recvFaceURL) {
        this.recvFaceURL = recvFaceURL;
    }

    public Map<String, Boolean> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Boolean> options) {
        this.options = options;
    }

    public String getAttachedInfo() {
        return attachedInfo;
    }

    public void setAttachedInfo(String attachedInfo) {
        this.attachedInfo = attachedInfo;
    }

    public OfflinePushInfo getOfflinePushInfo() {
        return offlinePushInfo;
    }

    public void setOfflinePushInfo(OfflinePushInfo offlinePushInfo) {
        this.offlinePushInfo = offlinePushInfo;
    }

    public String getUniqueID() {
        return uniqueID;
    }

    public void setUniqueID(String uniqueID) {
        this.uniqueID = uniqueID;
    }

    public Integer getSenderPlatformID() {
        return senderPlatformID;
    }

    public void setSenderPlatformID(Integer senderPlatformID) {
        this.senderPlatformID = senderPlatformID;
    }

    public Integer getRecvPlatformID() {
        return recvPlatformID;
    }

    public void setRecvPlatformID(Integer recvPlatformID) {
        this.recvPlatformID = recvPlatformID;
    }

    public Integer getMsgFrom() {
        return msgFrom;
    }

    public void setMsgFrom(Integer msgFrom) {
        this.msgFrom = msgFrom;
    }

    public Integer getSubType() {
        return subType;
    }

    public void setSubType(Integer subType) {
        this.subType = subType;
    }
}
