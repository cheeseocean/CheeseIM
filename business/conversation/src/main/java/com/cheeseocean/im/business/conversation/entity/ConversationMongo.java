package com.cheeseocean.im.business.conversation.entity;

import com.cheeseocean.im.business.conversation.api.param.Conversation;
import org.springframework.beans.BeanUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话MongoDB实体类
 * 严格按照OpenIM的Conversation模型设计
 * 
 * @author CheeseIM
 */
@Document(collection = "conversations")
@CompoundIndexes({
    @CompoundIndex(name = "owner_conversation_idx", def = "{'owner_user_id': 1, 'conversation_id': 1}", unique = true),
    @CompoundIndex(name = "owner_type_idx", def = "{'owner_user_id': 1, 'conversation_type': 1}"),
    @CompoundIndex(name = "owner_pinned_idx", def = "{'owner_user_id': 1, 'is_pinned': -1}"),
    @CompoundIndex(name = "conversation_id_idx", def = "{'conversation_id': 1}")
})
public class ConversationMongo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    private String id;
    
    @Field("owner_user_id")
    @Indexed
    private String ownerUserID;
    
    @Field("conversation_id")
    @Indexed
    private String conversationID;
    
    @Field("conversation_type")
    private Integer conversationType;
    
    @Field("user_id")
    private String userID;
    
    @Field("group_id")
    private String groupID;
    
    @Field("recv_msg_opt")
    private Integer recvMsgOpt;
    
    @Field("is_pinned")
    private Boolean isPinned;
    
    @Field("is_private_chat")
    private Boolean isPrivateChat;
    
    @Field("burn_duration")
    private Integer burnDuration;
    
    @Field("group_at_type")
    private Integer groupAtType;
    
    @Field("attached_info")
    private String attachedInfo;
    
    @Field("ex")
    private String ex;
    
    @Field("max_seq")
    private Long maxSeq;
    
    @Field("min_seq")
    private Long minSeq;
    
    @Field("create_time")
    private LocalDateTime createTime;
    
    @Field("is_msg_destruct")
    private Boolean isMsgDestruct;
    
    @Field("msg_destruct_time")
    private Long msgDestructTime;
    
    @Field("latest_msg_destruct_time")
    private LocalDateTime latestMsgDestructTime;
    
    public ConversationMongo() {
        this.conversationType = 0;
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
        this.latestMsgDestructTime = LocalDateTime.of(1, 1, 1, 0, 0, 0);
    }
    
    // Getter and Setter methods
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
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
    
    public Integer getRecvMsgOpt() {
        return recvMsgOpt;
    }
    
    public void setRecvMsgOpt(Integer recvMsgOpt) {
        this.recvMsgOpt = recvMsgOpt;
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
    
    /**
     * 转换为Conversation对象
     */
    public Conversation toConversation() {
        Conversation conversation = new Conversation();
        BeanUtils.copyProperties(this, conversation);
        return conversation;
    }
    
    /**
     * 从Conversation对象创建ConversationMongo
     */
    public static ConversationMongo fromConversation(Conversation conversation) {
        ConversationMongo mongo = new ConversationMongo();
        BeanUtils.copyProperties(conversation, mongo);
        return mongo;
    }
}
