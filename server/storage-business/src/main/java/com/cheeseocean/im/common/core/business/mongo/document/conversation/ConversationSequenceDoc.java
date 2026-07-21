package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 会话级别最大最小序列号 MongoDB 持久化文档。集合：{@code conversation_sequence}。
 *
 * <p>主键："{conversationId}"
 * 文档极简，只保存两个序列水位
 * <ul>
 *   <li>maxSeq — 服务端已分配最大消息 seq</li>
 *   <li>minSeq — 历史可见下界</li>
 * </ul>
 */
@Document("conversation_sequence")
@Data
public class ConversationSequenceDoc {

    /**
     * _id = "{conversationId}"
     */
    @Id
    private String id;

    /** 会话唯一标识。 */
    private String conversationId;
    /** 当前会话已分配的最大消息序列号。 */
    private long   maxSeq;
    /** 当前会话历史消息仍可见的最小序列号。 */
    private long   minSeq;
}
