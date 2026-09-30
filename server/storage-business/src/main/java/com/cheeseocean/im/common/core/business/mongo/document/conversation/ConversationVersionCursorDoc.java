package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 用户会话版本的原子分配游标，不参与 TTL 清理。
 *
 * @author wxc
 */
@Data
@Document("conversation_version_cursor")
public class ConversationVersionCursorDoc {

    @Id
    private String ownerUserId;
    private String versionId;
    private Long version;
}
