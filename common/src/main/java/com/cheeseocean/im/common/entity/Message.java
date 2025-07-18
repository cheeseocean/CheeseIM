package com.cheeseocean.im.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息实体类
 * 
 * @author CheeseIM
 */
public class Message implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 消息ID
     */
    @JsonProperty("clientMsgID")
    private String clientMsgID;
    
    /**
     * 服务端消息ID
     */
    @JsonProperty("serverMsgID")
    private String serverMsgID;
    
    /**
     * 发送者ID
     */
    @JsonProperty("sendID")
    private String sendID;
    
    /**
     * 接收者ID
     */
    @JsonProperty("recvID")
    private String recvID;
    
    /**
     * 群组ID (群聊时使用)
     */
    @JsonProperty("groupID")
    private String groupID;
    
    /**
     * 消息内容
     */
    @JsonProperty("content")
    private String content;
    
    /**
     * 消息类型 (1:文本 2:图片 3:语音 4:视频 5:文件)
     */
    @JsonProperty("contentType")
    private Integer contentType;
    
    /**
     * 会话类型 (1:单聊 2:群聊)
     */
    @JsonProperty("sessionType")
    private Integer sessionType;
    
    /**
     * 发送时间
     */
    @JsonProperty("sendTime")
    private Long sendTime;
    
    /**
     * 创建时间
     */
    @JsonProperty("createTime")
    private Long createTime;
    
    /**
     * 消息状态 (1:发送中 2:发送成功 3:发送失败)
     */
    @JsonProperty("status")
    private Integer status;
    
    /**
     * 消息序列号
     */
    @JsonProperty("seq")
    private Long seq;
    
    /**
     * 是否已读
     */
    @JsonProperty("isRead")
    private Boolean isRead;
    
    /**
     * 平台类型 (1:iOS 2:Android 3:Windows 4:OSX 5:WEB 6:MiniWeb 7:Linux)
     */
    @JsonProperty("platformID")
    private Integer platformID;
    
    /**
     * 扩展字段
     */
    @JsonProperty("ex")
    private String ex;
    
    // 构造函数
    public Message() {
        this.createTime = System.currentTimeMillis();
        this.sendTime = System.currentTimeMillis();
        this.status = 1; // 默认发送中
        this.isRead = false;
    }
    
    // Getter and Setter methods
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
    
    @Override
    public String toString() {
        return "Message{" +
                "clientMsgID='" + clientMsgID + '\'' +
                ", serverMsgID='" + serverMsgID + '\'' +
                ", sendID='" + sendID + '\'' +
                ", recvID='" + recvID + '\'' +
                ", groupID='" + groupID + '\'' +
                ", content='" + content + '\'' +
                ", contentType=" + contentType +
                ", sessionType=" + sessionType +
                ", sendTime=" + sendTime +
                ", createTime=" + createTime +
                ", status=" + status +
                ", seq=" + seq +
                ", isRead=" + isRead +
                ", platformID=" + platformID +
                ", ex='" + ex + '\'' +
                '}';
    }
}
