package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * 会话控制事件 outbox MongoDB 文档。
 */
@Document("conversation_control_event")
@CompoundIndexes({
        @CompoundIndex(name = "target_cursor", def = "{'targetUserIds': 1, 'cursor': 1}"),
        @CompoundIndex(name = "pending_by_shard", def = "{'cursorShard': 1, 'deliveryStateCode': 1, 'cursor': 1, 'expiresAt': 1}"),
        @CompoundIndex(name = "expired_claim_by_shard", def = "{'cursorShard': 1, 'deliveryStateCode': 1, 'claimExpiresAt': 1, 'cursor': 1}")
})
@Data
public class ConversationControlEventDoc {

    @Id
    private String id;
    private long cursor;
    private int cursorShard;
    private String conversationId;
    private int typeCode;
    private List<String> targetUserIds;
    private String payload;
    private int deliveryStateCode;
    private int deliveryAttempt;
    private String claimToken;
    private Instant claimExpiresAt;
    private Instant deliveredAt;
    private Instant createdAt;

    /** 到期后由 Mongo TTL 索引自动删除，确保瞬时状态不会长期堆积。 */
    @Indexed(name = "idx_conversation_control_event_expires_at_ttl", expireAfterSeconds = 0)
    private Instant expiresAt;
}
