package com.cheeseocean.im.common.api.business.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户-会话同步位点领域对象。
 *
 * <p>记录单个用户在某条会话中的读写水位线，用于计算未读数和支持增量拉取。
 * readSeq/maxSeq/minSeq 默认值必须为 0L，不允许 null。
 */
@Data
public class UserConversationSyncPoint implements Serializable {

    /**
     * 文档唯一标识（"{userId}:{conversationId}"）
     */
    private String id;
    /**
     * 用户 ID
     */
    private String userId;
    /**
     * 会话 ID
     */
    private String conversationId;
    /**
     * 用户已读水位线（用户明确已读到的最大序列号）。
     * 默认值 0L。
     */
    private long   readSeq = 0L;
    /**
     * 用户收到的最大序列号（已下发到该用户设备的最新 seq）。
     * 默认值 0L。
     */
    private long   maxSeq  = 0L;
    /**
     * 用户可见的最小序列号（被加入会话时的消息起始位点）。
     * 默认值 0L。
     */
    private long   minSeq  = 0L;

    // ── 领域方法 ─────────────────────────────────────────────────────────────

    /**
     * 根据当前最大水位和已读水位计算未读条数。
     */
    public long getUnreadCount() {
        return Math.max(0L, maxSeq - readSeq);
    }

}
