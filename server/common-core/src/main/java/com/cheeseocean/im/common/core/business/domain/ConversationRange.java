package com.cheeseocean.im.common.core.business.domain;

/**
 * 会话级序列范围领域对象。
 *
 * <p>仅表达某条会话当前可见的全局消息水位，不携带任何用户维度字段。
 */
public class ConversationRange {

    /** 会话唯一标识 */
    private String conversationId;

    /** 当前会话已分配的最大消息序列号 */
    private long maxSeq;

    /** 当前会话仍可见的最小消息序列号 */
    private long minSeq;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public long getMaxSeq() { return maxSeq; }
    public void setMaxSeq(long maxSeq) { this.maxSeq = maxSeq; }

    public long getMinSeq() { return minSeq; }
    public void setMinSeq(long minSeq) { this.minSeq = minSeq; }
}
