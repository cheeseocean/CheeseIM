package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import com.cheeseocean.im.common.api.enums.ConversationVersionOperation;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
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

    /** 版本日志只服务增量同步窗口，长期历史由会话与消息主表承载。 */
    public static final int VERSION_LOG_TTL_SECONDS = 180 * 24 * 60 * 60;

    @Id
    private String id;

    private String ownerUserId;
    private String versionId;
    private long version;
    private String conversationId;
    private ConversationVersionOperation operation;

    @Indexed(name = "idx_conversation_version_log_created_at_ttl",
            expireAfterSeconds = VERSION_LOG_TTL_SECONDS)
    private Instant createdAt;
}
