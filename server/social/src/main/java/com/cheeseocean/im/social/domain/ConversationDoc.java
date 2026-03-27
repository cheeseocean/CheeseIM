package com.cheeseocean.im.social.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for a per-user Conversation record.
 *
 * Collection: {@code conversation}
 *
 * _id strategy: "{ownerUserId}:{conversationId}" — a deterministic compound
 * key that makes every upsert idempotent and avoids a separate unique index.
 *
 * Indexes:
 *   • _id (unique, default)
 *   • {ownerUserId, updatedAt desc} — drives the "recent conversations" list query
 */
@Document("conversation")
@CompoundIndexes({
        @CompoundIndex(name = "owner_updated", def = "{'ownerUserId': 1, 'updatedAt': -1}")
})
public class ConversationDoc {

    @Id
    private String id;             // ownerUserId:conversationId

    private String ownerUserId;
    private String conversationId;
    private int    conversationType;   // 1=single, 2=group, 3=notification
    private String targetId;           // single: other party; group: groupId

    /** 取值见 {@link com.cheeseocean.im.common.core.enums.RecvMsgOpt}。 */
    private int    recvMsgOpt;
    private int    unreadCount;
    private Long   latestMsgSeq;
    private String latestMsg;          // JSON ConversationLastMessageSummary
    private Long   readSeq;

    private boolean pinned;
    private String  draftText;
    private String  attachedInfo;

    private Instant createdAt;
    private Instant updatedAt;

    // ── getters / setters ────────────────────────────────────────────────────

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

    public Long getReadSeq() { return readSeq; }
    public void setReadSeq(Long readSeq) { this.readSeq = readSeq; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public String getDraftText() { return draftText; }
    public void setDraftText(String draftText) { this.draftText = draftText; }

    public String getAttachedInfo() { return attachedInfo; }
    public void setAttachedInfo(String attachedInfo) { this.attachedInfo = attachedInfo; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
