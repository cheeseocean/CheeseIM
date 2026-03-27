package com.cheeseocean.im.social.model;

/**
 * 用户维度的会话视图，持久化至 MongoDB。
 * 一个物理会话（conversationId）会为每位参与者各生成一条 Conversation 记录，
 * 符合"写扩散"模型。
 */
public class Conversation {

    /** The user who owns this conversation entry (index key). */
    private String ownerUserId;

    /** Physical conversation identifier (e.g. si_A_B or g_GID). */
    private String conversationId;

    /** 1=single, 2=group, 3=notification — mirrors SessionType enum. */
    private int conversationType;

    /** For single chat: the other party's userId. For group chat: the groupId. */
    private String targetId;

    /** 取值见 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt}。 */
    private int recvMsgOpt;

    private int unreadCount;

    private Long latestMsgSeq;

    /** JSON-serialised ConversationLastMessageSummary, used for list display. */
    private String latestMsg;

    private boolean pinned;

    private String draftText;

    private String attachedInfo;

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
}
