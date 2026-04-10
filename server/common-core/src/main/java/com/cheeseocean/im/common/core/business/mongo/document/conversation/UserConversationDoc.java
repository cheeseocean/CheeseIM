package com.cheeseocean.im.common.core.business.mongo.document.conversation;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 用户-会话业务状态 MongoDB 持久化文档。集合：{@code conversation}。
 *
 * <p>主键："{ownerUserId}:{conversationId}"（确定性复合主键，upsert 幂等）。
 * 序列号字段（maxSeq / minSeq ）独立存储在 {@code conversation_seq_range} 集合，
 * 参见 {@link ConversationRangeDoc}。
 */
@Document("conversation")
@CompoundIndexes({
        @CompoundIndex(name = "uniq_owner_conversation", def = "{'ownerUserId': 1, 'conversationId': 1}", unique = true),
        @CompoundIndex(name = "owner_updated", def = "{'ownerUserId': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "owner_pinned_updated", def = "{'ownerUserId': 1, 'pinned': -1, 'updatedAt': -1}")
})
@Data
public class UserConversationDoc {

    @Id
    private String id;

    /**
     * 会话所属用户 ID。
     */
    private String  ownerUserId;
    /**
     * 会话唯一标识。
     */
    private String  conversationId;
    /**
     * 会话类型编码，区分单聊、群聊、通知等。
     */
    private int     conversationType;
    /**
     * 单聊对端用户 ID 或群聊目标 ID。
     */
    private String  targetId;
    /**
     * 消息接收选项编码。
     */
    private int     receiveOpt;
    /**
     * 是否置顶该会话。
     */
    private boolean pinned;
    /**
     * 附件信息
     */
    private String  attachedInfo;
    /**
     * 群聊 @ 状态编码。
     */
    private int     groupAtType;
    /**
     * 是否开启消息自动销毁。
     */
    private boolean autoCleanup;
    /**
     * 消息自动销毁时长，单位秒。
     */
    private long    autoCleanupCycle;
    /**
     * 最近一次消息自动销毁处理时间戳。
     */
    private long    latestCleanupTime;
    /**
     * 会话创建时间。
     */
    private Instant createdAt;
    /**
     * 会话最近更新时间。
     */
    private Instant updatedAt;

}
