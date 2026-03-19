package com.cheeseocean.im.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

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

    /**
     * 发送者昵称
     */
    @JsonProperty("senderNickname")
    private String senderNickname;

    /**
     * 发送者头像
     */
    @JsonProperty("senderFaceURL")
    private String senderFaceURL;

    /**
     * 接收者昵称
     */
    @JsonProperty("recvNickname")
    private String recvNickname;

    /**
     * 接收者头像
     */
    @JsonProperty("recvFaceURL")
    private String recvFaceURL;

    /**
     * 消息选项配置
     */
    @JsonProperty("options")
    private Map<String, Boolean> options;

    /**
     * 附加信息
     */
    @JsonProperty("attachedInfo")
    private String attachedInfo;

    /**
     * 离线推送信息
     */
    @JsonProperty("offlinePushInfo")
    private OfflinePushInfo offlinePushInfo;

    /**
     * 消息唯一序列号
     */
    @JsonProperty("uniqueID")
    private String uniqueID;

    /**
     * 消息发送者平台ID
     */
    @JsonProperty("senderPlatformID")
    private Integer senderPlatformID;

    /**
     * 消息接收者平台ID
     */
    @JsonProperty("recvPlatformID")
    private Integer recvPlatformID;

    /**
     * 消息类型（系统消息、用户消息等）
     */
    @JsonProperty("msgFrom")
    private Integer msgFrom;

    /**
     * 消息子类型
     */
    @JsonProperty("subType")
    private Integer subType;
    
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
                ", senderNickname='" + senderNickname + '\'' +
                ", senderFaceURL='" + senderFaceURL + '\'' +
                ", recvNickname='" + recvNickname + '\'' +
                ", recvFaceURL='" + recvFaceURL + '\'' +
                ", options=" + options +
                ", attachedInfo='" + attachedInfo + '\'' +
                ", offlinePushInfo=" + offlinePushInfo +
                ", uniqueID='" + uniqueID + '\'' +
                ", senderPlatformID=" + senderPlatformID +
                ", recvPlatformID=" + recvPlatformID +
                ", msgFrom=" + msgFrom +
                ", subType=" + subType +
                '}';
    }
}
