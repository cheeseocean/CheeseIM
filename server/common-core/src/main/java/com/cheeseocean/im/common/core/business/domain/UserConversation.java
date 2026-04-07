package com.cheeseocean.im.common.core.business.domain;

import com.cheeseocean.im.common.api.enums.ReceiveOption;

/**
 * 用户-会话业务状态领域对象（写扩散模型）。
 *
 * <p>每位参与者拥有独立的一条记录，存储会话的个性化配置（置顶、免打扰、草稿等）
 * 及用于会话列表展示的聚合信息（最新消息摘要、未读计数）。
 *
 * <p>序列号相关字段（maxSeq / minSeq / readSeq）独立存储在
 * {@link UserConversationSyncPoint}，避免高频已读回执写入污染本表。
 */
public class UserConversation {

    /** 会话所属者用户 ID */
    private String ownerUserId;

    /** 会话唯一标识（如 si_{A}_{B} 或 sg_{groupId}） */
    private String conversationId;

    /** 会话类型：1=单聊，2=普通群聊，3=通知 */
    private int conversationType;

    /** 单聊对端用户 ID 或群聊的 groupId */
    private String targetId;

    /**
     * 免打扰开关。
     * 0=正常接收，1=不收消息，2=收不提醒。
     * 取值见 {@link ReceiveOption}。
     */
    private int recvMsgOpt;

    /** 当前未读消息数（由消息投递增量维护，标记已读时归零） */
    private int unreadCount;

    /** 最新消息的序列号（用于会话列表排序） */
    private Long latestMsgSeq;

    /** 最新消息摘要（JSON，用于会话列表展示） */
    private String latestMsg;

    /** 是否置顶该会话 */
    private boolean pinned;

    /** 草稿文本 */
    private String draftText;

    /** 强提醒元数据（JSON 字符串） */
    private String attachedInfo;

    /**
     * 群 @ 强提醒类型。
     * 取值见 {@link com.cheeseocean.im.common.api.enums.GroupAtTypeEnum}。
     */
    private int groupAtType;

    /** 是否开启阅后即焚 */
    private boolean privateChat;

    /** 阅后即焚倒计时（秒） */
    private int burnDuration;

    /** 是否开启消息自动清理 */
    private boolean msgDestruct;

    /** 消息自动清理周期（秒） */
    private long msgDestructTime;

    /** 最近一次执行消息清理的时间（毫秒时间戳） */
    private long latestMsgDestructTime;

    /** 会话首次激活时间（毫秒时间戳） */
    private long createdAt;

    /** 最近更新时间（毫秒时间戳） */
    private long updatedAt;

    // ── getters / setters ────────────────────────────────────────────────────

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public int getConversationType() { return conversationType; }
    public void setConversationType(int conversationType) { this.conversationType = conversationType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public int getRecvMsgOpt() { return recvMsgOpt; }
    public void setRecvMsgOpt(int recvMsgOpt) { this.recvMsgOpt = recvMsgOpt; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    public Long getLatestMsgSeq() { return latestMsgSeq; }
    public void setLatestMsgSeq(Long latestMsgSeq) { this.latestMsgSeq = latestMsgSeq; }

    public String getLatestMsg() { return latestMsg; }
    public void setLatestMsg(String latestMsg) { this.latestMsg = latestMsg; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public String getDraftText() { return draftText; }
    public void setDraftText(String draftText) { this.draftText = draftText; }

    public String getAttachedInfo() { return attachedInfo; }
    public void setAttachedInfo(String attachedInfo) { this.attachedInfo = attachedInfo; }

    public int getGroupAtType() { return groupAtType; }
    public void setGroupAtType(int groupAtType) { this.groupAtType = groupAtType; }

    public boolean isPrivateChat() { return privateChat; }
    public void setPrivateChat(boolean privateChat) { this.privateChat = privateChat; }

    public int getBurnDuration() { return burnDuration; }
    public void setBurnDuration(int burnDuration) { this.burnDuration = burnDuration; }

    public boolean isMsgDestruct() { return msgDestruct; }
    public void setMsgDestruct(boolean msgDestruct) { this.msgDestruct = msgDestruct; }

    public long getMsgDestructTime() { return msgDestructTime; }
    public void setMsgDestructTime(long msgDestructTime) { this.msgDestructTime = msgDestructTime; }

    public long getLatestMsgDestructTime() { return latestMsgDestructTime; }
    public void setLatestMsgDestructTime(long latestMsgDestructTime) { this.latestMsgDestructTime = latestMsgDestructTime; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
