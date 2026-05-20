package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import com.cheeseocean.im.common.api.enums.ConversationVersionOperation;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 用户会话元数据版本日志 MongoDB 文档。
 */
@Document("conversation_version_log")
@CompoundIndexes({
        @CompoundIndex(name = "owner_version", def = "{'ownerUserId': 1, 'version': -1}"),
        @CompoundIndex(name = "owner_version_id_version", def = "{'ownerUserId': 1, 'versionId': 1, 'version': 1}")
})
@Data
public class ConversationVersionLogDoc {

    @Id
    private String id;

    private String ownerUserId;
    private String versionId;
    private long version;
    private String conversationId;
    private ConversationVersionOperation operation;
    private Instant createdAt;
}
