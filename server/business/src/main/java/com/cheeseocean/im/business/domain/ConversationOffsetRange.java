package com.cheeseocean.im.business.domain;

/**
 * 用户-会话同步偏移量领域对象（per-user 协议同步表）。
 *
 * <p>记录每位用户在某个会话中的三个关键序列水位：
 * <ul>
 *   <li>{@link #maxSeq}：服务端该会话已分配的最大消息序列号，客户端拉取的上界。</li>
 *   <li>{@link #minSeq}：历史消息可见下界，小于此值的消息已被清理，无法拉取。</li>
 *   <li>{@link #readSeq}：用户已确认读取到的序列号（已读水位线），用于计算未读数与多端同步。</li>
 * </ul>
 *
 * <p>该对象刻意保持极简，与 {@link UserConversationState} 分离存储：
 * 已读回执（read receipt）写入频率极高，若与包含几十个字段的业务会话文档合并，
 * 会导致高并发写冲突和 MongoDB 大文档更新开销。
 */
public class ConversationOffsetRange {

    /** 会话所属者用户 ID */
    private String ownerUserId;

    /** 会话唯一标识 */
    private String conversationId;

    /**
     * 服务端当前最大消息序列号。
     * 客户端同步时以此为拉取上界；0 表示该会话尚无消息。
     */
    private long maxSeq = 0L;

    /**
     * 历史消息可见起始序列号。
     * 小于此值的消息已被清理，SDK 不应尝试拉取。
     */
    private long minSeq = 0L;

    /**
     * 用户已读水位线。
     * 客户端最后一次确认已读的消息序列号；0 表示尚未阅读。
     */
    private long readSeq = 0L;

    // ── getters / setters ────────────────────────────────────────────────────

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public long getMaxSeq() { return maxSeq; }
    public void setMaxSeq(long maxSeq) { this.maxSeq = maxSeq; }

    public long getMinSeq() { return minSeq; }
    public void setMinSeq(long minSeq) { this.minSeq = minSeq; }

    public long getReadSeq() { return readSeq; }
    public void setReadSeq(long readSeq) { this.readSeq = readSeq; }
}
