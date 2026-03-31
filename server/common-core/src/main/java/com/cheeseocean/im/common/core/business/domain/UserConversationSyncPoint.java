package com.cheeseocean.im.common.core.business.domain;

/**
 * 用户-会话同步位点领域对象。
 *
 * <p>记录单个用户在某条会话中的读写水位线，用于计算未读数和支持增量拉取。
 * readSeq/maxSeq/minSeq 默认值必须为 0L，不允许 null。
 */
public class UserConversationSyncPoint {

    /** 文档唯一标识（"{userId}:{conversationId}"） */
    private String id;

    /** 用户 ID */
    private String userId;

    /** 会话 ID */
    private String conversationId;

    /**
     * 用户已读水位线（用户明确已读到的最大序列号）。
     * 默认值 0L。
     */
    private long readSeq = 0L;

    /**
     * 用户收到的最大序列号（已下发到该用户设备的最新 seq）。
     * 默认值 0L。
     */
    private long maxSeq = 0L;

    /**
     * 用户可见的最小序列号（被加入会话时的消息起始位点）。
     * 默认值 0L。
     */
    private long minSeq = 0L;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /**
     * 计算该会话的当前未读消息数。
     */
    public long getUnreadCount() {
        return Math.max(0L, maxSeq - readSeq);
    }

    // ── getters / setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public long getReadSeq() { return readSeq; }
    public void setReadSeq(long readSeq) { this.readSeq = readSeq; }

    public long getMaxSeq() { return maxSeq; }
    public void setMaxSeq(long maxSeq) { this.maxSeq = maxSeq; }

    public long getMinSeq() { return minSeq; }
    public void setMinSeq(long minSeq) { this.minSeq = minSeq; }
}
