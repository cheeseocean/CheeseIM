package com.cheeseocean.im.common.core.business.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 用户-会话同步位点 MongoDB 持久化文档。集合：{@code user_sync_checkpoint}。
 */
@Document("user_sync_checkpoint")
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_conv", def = "{'userId': 1, 'conversationId': 1}", unique = true)
})
public class UserSyncCheckpointDoc {

    @Id
    private String id;

    private String userId;
    private String conversationId;
    private long readSeq;
    private long maxSeq;
    private long minSeq;

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
