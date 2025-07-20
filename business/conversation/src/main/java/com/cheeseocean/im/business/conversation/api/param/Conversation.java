package com.cheeseocean.im.business.conversation.api.param;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话实体类
 * 严格按照OpenIM的Conversation模型设计
 *
 * @author CheeseIM
 */
public class Conversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话所有者用户ID
     */
    @JsonProperty("ownerUserID")
    private String ownerUserID;

    /**
     * 会话ID
     */
    @JsonProperty("conversationID")
    private String conversationID;

    /**
     * 会话类型
     */
    @JsonProperty("conversationType")
    private Integer conversationType;

    /**
     * 用户ID
     */
    @JsonProperty("userID")
    private String userID;

    /**
     * 群组ID
     */
    @JsonProperty("groupID")
    private String groupID;

    /**
     * 消息接收选项
     */
    @JsonProperty("recvMsgOpt")
    private Integer recvMsgOpt;
    
    /**
     * 是否置顶
     */
    @JsonProperty("isPinned")
    private Boolean isPinned;

    /**
     * 是否私聊
     */
    @JsonProperty("isPrivateChat")
    private Boolean isPrivateChat;

    /**
     * 燃烧持续时间
     */
    @JsonProperty("burnDuration")
    private Integer burnDuration;

    /**
     * 群@类型
     */
    @JsonProperty("groupAtType")
    private Integer groupAtType;

    /**
     * 附加信息
     */
    @JsonProperty("attachedInfo")
    private String attachedInfo;

    /**
     * 扩展字段
     */
    @JsonProperty("ex")
    private String ex;

    /**
     * 最大序列号
     */
    @JsonProperty("maxSeq")
    private Long maxSeq;

    /**
     * 最小序列号
     */
    @JsonProperty("minSeq")
    private Long minSeq;

    /**
     * 创建时间
     */
    @JsonProperty("createTime")
    private LocalDateTime createTime;

    /**
     * 是否消息销毁
     */
    @JsonProperty("isMsgDestruct")
    private Boolean isMsgDestruct;

    /**
     * 消息销毁时间
     */
    @JsonProperty("msgDestructTime")
    private Long msgDestructTime;

    /**
     * 最新消息销毁时间
     */
    @JsonProperty("latestMsgDestructTime")
    private LocalDateTime latestMsgDestructTime;
    
    // 构造函数
    public Conversation() {
        this.recvMsgOpt = 0;
        this.isPinned = false;
        this.isPrivateChat = false;
        this.burnDuration = 0;
        this.groupAtType = 0;
        this.maxSeq = 0L;
        this.minSeq = 0L;
        this.createTime = LocalDateTime.now();
        this.isMsgDestruct = false;
        this.msgDestructTime = 0L;
        this.latestMsgDestructTime = LocalDateTime.of(1, 1, 1, 0, 0, 0); // OpenIM默认值
    }
    
    // Getter and Setter methods
    public String getOwnerUserID() {
        return ownerUserID;
    }

    public void setOwnerUserID(String ownerUserID) {
        this.ownerUserID = ownerUserID;
    }

    public String getConversationID() {
        return conversationID;
    }

    public void setConversationID(String conversationID) {
        this.conversationID = conversationID;
    }

    public Integer getRecvMsgOpt() {
        return recvMsgOpt;
    }

    public void setRecvMsgOpt(Integer recvMsgOpt) {
        this.recvMsgOpt = recvMsgOpt;
    }

    public Integer getConversationType() {
        return conversationType;
    }

    public void setConversationType(Integer conversationType) {
        this.conversationType = conversationType;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getGroupID() {
        return groupID;
    }

    public void setGroupID(String groupID) {
        this.groupID = groupID;
    }
    
    public Boolean getIsPinned() {
        return isPinned;
    }

    public void setIsPinned(Boolean isPinned) {
        this.isPinned = isPinned;
    }

    public Boolean getIsPrivateChat() {
        return isPrivateChat;
    }

    public void setIsPrivateChat(Boolean isPrivateChat) {
        this.isPrivateChat = isPrivateChat;
    }

    public Integer getBurnDuration() {
        return burnDuration;
    }

    public void setBurnDuration(Integer burnDuration) {
        this.burnDuration = burnDuration;
    }

    public Integer getGroupAtType() {
        return groupAtType;
    }

    public void setGroupAtType(Integer groupAtType) {
        this.groupAtType = groupAtType;
    }

    public String getAttachedInfo() {
        return attachedInfo;
    }

    public void setAttachedInfo(String attachedInfo) {
        this.attachedInfo = attachedInfo;
    }

    public String getEx() {
        return ex;
    }

    public void setEx(String ex) {
        this.ex = ex;
    }
    
    public Long getMaxSeq() {
        return maxSeq;
    }

    public void setMaxSeq(Long maxSeq) {
        this.maxSeq = maxSeq;
    }

    public Long getMinSeq() {
        return minSeq;
    }

    public void setMinSeq(Long minSeq) {
        this.minSeq = minSeq;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Boolean getIsMsgDestruct() {
        return isMsgDestruct;
    }

    public void setIsMsgDestruct(Boolean isMsgDestruct) {
        this.isMsgDestruct = isMsgDestruct;
    }

    public Long getMsgDestructTime() {
        return msgDestructTime;
    }

    public void setMsgDestructTime(Long msgDestructTime) {
        this.msgDestructTime = msgDestructTime;
    }

    public LocalDateTime getLatestMsgDestructTime() {
        return latestMsgDestructTime;
    }

    public void setLatestMsgDestructTime(LocalDateTime latestMsgDestructTime) {
        this.latestMsgDestructTime = latestMsgDestructTime;
    }
    
    @Override
    public String toString() {
        return "Conversation{" +
                "ownerUserID='" + ownerUserID + '\'' +
                ", conversationID='" + conversationID + '\'' +
                ", conversationType=" + conversationType +
                ", userID='" + userID + '\'' +
                ", groupID='" + groupID + '\'' +
                ", recvMsgOpt=" + recvMsgOpt +
                ", isPinned=" + isPinned +
                ", isPrivateChat=" + isPrivateChat +
                ", burnDuration=" + burnDuration +
                ", groupAtType=" + groupAtType +
                ", attachedInfo='" + attachedInfo + '\'' +
                ", ex='" + ex + '\'' +
                ", maxSeq=" + maxSeq +
                ", minSeq=" + minSeq +
                ", createTime=" + createTime +
                ", isMsgDestruct=" + isMsgDestruct +
                ", msgDestructTime=" + msgDestructTime +
                ", latestMsgDestructTime=" + latestMsgDestructTime +
                '}';
    }
}
