package com.cheeseocean.im.business.infra.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 用户-会话业务状态 MongoDB 持久化文档。集合：{@code conversation}。
 *
 * <p>主键："{ownerUserId}:{conversationId}"（确定性复合主键，upsert 幂等）。
 * 序列号字段（maxSeq / minSeq / readSeq）独立存储在 {@code seq_user} 集合，
 * 参见 {@link ConversationOffsetRangeDoc}。
 */
@Document("conversation")
@CompoundIndexes({
        @CompoundIndex(name = "owner_updated", def = "{'ownerUserId': 1, 'updatedAt': -1}")
})
public class UserConversationStateDoc {

    @Id
    private String id;

    private String ownerUserId;
    private String conversationId;
    private int conversationType;
    private String targetId;
    private int recvMsgOpt;
    private int unreadCount;
    private Long latestMsgSeq;
    private String latestMsg;
    private boolean pinned;
    private String draftText;
    private String attachedInfo;
    private int groupAtType;
    private boolean isPrivateChat;
    private int burnDuration;
    private boolean isMsgDestruct;
    private long msgDestructTime;
    private long latestMsgDestructTime;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public boolean isPrivateChat() { return isPrivateChat; }
    public void setPrivateChat(boolean privateChat) { isPrivateChat = privateChat; }

    public int getBurnDuration() { return burnDuration; }
    public void setBurnDuration(int burnDuration) { this.burnDuration = burnDuration; }

    public boolean isMsgDestruct() { return isMsgDestruct; }
    public void setMsgDestruct(boolean msgDestruct) { isMsgDestruct = msgDestruct; }

    public long getMsgDestructTime() { return msgDestructTime; }
    public void setMsgDestructTime(long msgDestructTime) { this.msgDestructTime = msgDestructTime; }

    public long getLatestMsgDestructTime() { return latestMsgDestructTime; }
    public void setLatestMsgDestructTime(long latestMsgDestructTime) { this.latestMsgDestructTime = latestMsgDestructTime; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
