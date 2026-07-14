package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 会话控制事件全局游标计数器。
 *
 * <p>独立计数器避免使用时间戳作为游标时在并发写入下出现漏拉。
 */
@Document("conversation_control_event_cursor")
@Data
public class ConversationControlEventCursorDoc {

    public static final String GLOBAL_CURSOR_ID = "global";

    @Id
    private String id;
    private long cursor;
}
