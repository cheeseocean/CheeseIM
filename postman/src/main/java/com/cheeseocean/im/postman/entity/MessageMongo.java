package com.cheeseocean.im.postman.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.io.Serializable;

/**
 * MongoDB消息存储实体
 * 
 * @author CheeseIM
 */
@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "sendID_recvID_idx", def = "{'sendID': 1, 'recvID': 1, 'sendTime': -1}"),
    @CompoundIndex(name = "groupID_idx", def = "{'groupID': 1, 'sendTime': -1}"),
    @CompoundIndex(name = "conversationID_idx", def = "{'conversationID': 1, 'seq': -1}")
})
public class MessageMongo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    private String id;
    
    /**
     * 客户端消息ID
     */
    @Field("clientMsgID")
    @Indexed
    private String clientMsgID;
    
    /**
     * 服务端消息ID
     */
    @Field("serverMsgID")
    @Indexed(unique = true)
    private String serverMsgID;
    
    /**
     * 发送者ID
     */
    @Field("sendID")
    @Indexed
    private String sendID;
    
    /**
     * 接收者ID
     */
    @Field("recvID")
    @Indexed
    private String recvID;
    
    /**
     * 群组ID
     */
    @Field("groupID")
    @Indexed
    private String groupID;
    
    /**
     * 会话ID
     */
    @Field("conversationID")
    @Indexed
    private String conversationID;
    
    /**
     * 消息内容
     */
    @Field("content")
    private String content;
    
    /**
     * 消息类型
     */
    @Field("contentType")
    private Integer contentType;
    
    /**
     * 会话类型
     */
    @Field("sessionType")
    private Integer sessionType;
    
    /**
     * 发送时间
     */
    @Field("sendTime")
    @Indexed
    private Long sendTime;
    
    /**
     * 创建时间
     */
    @Field("createTime")
    private Long createTime;
    
    /**
     * 消息状态
     */
    @Field("status")
    private Integer status;
    
    /**
     * 消息序列号
     */
    @Field("seq")
    @Indexed
    private Long seq;
    
    /**
     * 是否已读
     */
    @Field("isRead")
    private Boolean isRead;
    
    /**
     * 平台类型
     */
    @Field("platformID")
    private Integer platformID;
    
    /**
     * 扩展字段
     */
    @Field("ex")
    private String ex;
    
    /**
     * 消息选项
     */
    @Field("options")
    private String options;
    
    /**
     * 离线推送信息
     */
    @Field("offlinePush")
    private String offlinePush;
    
    /**
     * 附加信息
     */
    @Field("attachedInfo")
    private String attachedInfo;
    
    // 构造函数
    public MessageMongo() {
    }
    
    // Getter and Setter methods
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
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
    
    public String getConversationID() {
        return conversationID;
    }
    
    public void setConversationID(String conversationID) {
        this.conversationID = conversationID;
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
    
    public String getOptions() {
        return options;
    }
    
    public void setOptions(String options) {
        this.options = options;
    }
    
    public String getOfflinePush() {
        return offlinePush;
    }
    
    public void setOfflinePush(String offlinePush) {
        this.offlinePush = offlinePush;
    }
    
    public String getAttachedInfo() {
        return attachedInfo;
    }
    
    public void setAttachedInfo(String attachedInfo) {
        this.attachedInfo = attachedInfo;
    }
}
