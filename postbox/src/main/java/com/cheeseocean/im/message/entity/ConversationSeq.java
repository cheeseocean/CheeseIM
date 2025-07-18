package com.cheeseocean.im.message.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import java.io.Serializable;

/**
 * 会话序列号实体
 * 用于管理每个会话的消息序列号
 * 
 * @author CheeseIM
 */
@Document(collection = "conversation_seqs")
public class ConversationSeq implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    private String id;
    
    /**
     * 会话ID
     */
    @Field("conversationID")
    @Indexed(unique = true)
    private String conversationID;
    
    /**
     * 当前序列号
     */
    @Field("seq")
    private Long seq;
    
    /**
     * 最大序列号
     */
    @Field("maxSeq")
    private Long maxSeq;
    
    /**
     * 最小序列号
     */
    @Field("minSeq")
    private Long minSeq;
    
    /**
     * 创建时间
     */
    @Field("createTime")
    private Long createTime;
    
    /**
     * 更新时间
     */
    @Field("updateTime")
    private Long updateTime;
    
    public ConversationSeq() {
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
        this.seq = 0L;
        this.maxSeq = 0L;
        this.minSeq = 0L;
    }
    
    public ConversationSeq(String conversationID) {
        this();
        this.conversationID = conversationID;
    }
    
    // Getter and Setter methods
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getConversationID() {
        return conversationID;
    }
    
    public void setConversationID(String conversationID) {
        this.conversationID = conversationID;
    }
    
    public Long getSeq() {
        return seq;
    }
    
    public void setSeq(Long seq) {
        this.seq = seq;
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
    
    public Long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
    
    public Long getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
    
    /**
     * 增加序列号
     */
    public Long incrementSeq() {
        this.seq++;
        this.maxSeq = Math.max(this.maxSeq, this.seq);
        if (this.minSeq == 0) {
            this.minSeq = this.seq;
        }
        this.updateTime = System.currentTimeMillis();
        return this.seq;
    }
}
