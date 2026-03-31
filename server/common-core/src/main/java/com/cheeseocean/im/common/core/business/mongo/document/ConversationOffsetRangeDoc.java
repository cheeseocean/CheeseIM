package com.cheeseocean.im.common.core.business.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 用户-会话同步偏移量 MongoDB 持久化文档。集合：{@code seq_user}。
 *
 * <p>主键："{ownerUserId}:{conversationId}"（确定性复合主键）。
 * 文档极简，只保存三个序列水位，专为高频写入（已读回执）优化：
 * <ul>
 *   <li>maxSeq — 服务端已分配最大消息 seq</li>
 *   <li>minSeq — 历史可见下界</li>
 *   <li>readSeq — 用户已读水位线</li>
 * </ul>
 */
@Document("seq_user")
@CompoundIndexes({
        @CompoundIndex(name = "idx_owner_conv", def = "{'ownerUserId': 1, 'conversationId': 1}", unique = true)
})
public class ConversationOffsetRangeDoc {

    /** _id = "{ownerUserId}:{conversationId}" */
    @Id
    private String id;

    private String ownerUserId;
    private String conversationId;
    private long maxSeq;
    private long minSeq;
    private long readSeq;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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
