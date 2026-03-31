package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 用户-会话同步位点 MongoDB 持久化文档。集合：{@code user_conversation_sync_point}。
 * 专为高频写入（已读回执）优化：
 *   <li>readSeq — 用户已读水位线</li>
 */
@Document("user_conversation_sync_point")
@CompoundIndexes({
        @CompoundIndex(name = "idx_user_conv", def = "{'userId': 1, 'conversationId': 1}", unique = true)
})
@Data
public class UserConversationSyncPointDoc {

    @Id
    private String id;

    /** 用户 ID。 */
    private String userId;
    /** 会话唯一标识。 */
    private String conversationId;
    /** 用户已读水位线。 */
    private long   readSeq;
    /** 用户当前已同步到的最大序列号。 */
    private long   maxSeq;
    /** 用户当前可见的最小序列号。 */
    private long   minSeq;
}
