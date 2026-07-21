package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 会话控制事件分片游标计数器。
 *
 * <p>固定分片避免单个全局文档成为写热点；同一用户始终映射到同一分片，客户端仍保存一个 long cursor。
 */
@Document("conversation_control_event_cursor")
@Data
public class ConversationControlEventCursorDoc {

    public static final String GLOBAL_CURSOR_ID = "global";

    public static String shardCursorId(int shardId) {
        return "shard:" + shardId;
    }

    @Id
    private String id;
    private long cursor;
}
