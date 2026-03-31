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
 * 序列号字段（maxSeq / minSeq ）独立存储在 {@code converstaion_seq_range} 集合，
 * 参见 {@link ConversationRangeDoc}。
 */
@Document("conversation")
@CompoundIndexes({
        @CompoundIndex(name = "owner_updated", def = "{'ownerUserId': 1, 'updatedAt': -1}")
})
@Data
public class UserConversationDoc {

    @Id
    private String id;

    /** 会话所属用户 ID。 */
    private String  ownerUserId;
    /** 会话唯一标识。 */
    private String  conversationId;
    /** 会话类型编码，区分单聊、群聊、通知等。 */
    private int     conversationType;
    /** 单聊对端用户 ID 或群聊目标 ID。 */
    private String  targetId;
    /** 消息接收选项编码。 */
    private int     recvMsgOpt;
    /** 当前会话未读消息数。 */
    private int     unreadCount;
    /** 会话最新消息序列号。 */
    private Long    latestMsgSeq;
    /** 会话最新消息摘要 JSON。 */
    private String  latestMsg;
    /** 是否置顶该会话。 */
    private boolean pinned;
    /** 会话草稿文本。 */
    private String  draftText;
    /** 会话附加信息 JSON。 */
    private String  attachedInfo;
    /** 群聊 @ 状态编码。 */
    private int     groupAtType;
    /** 是否开启阅后即焚。 */
    private boolean isPrivateChat;
    /** 阅后即焚时长，单位秒。 */
    private int     burnDuration;
    /** 是否开启消息自动销毁。 */
    private boolean isMsgDestruct;
    /** 消息自动销毁时长，单位秒。 */
    private long    msgDestructTime;
    /** 最近一次消息自动销毁处理时间戳。 */
    private long    latestMsgDestructTime;
    /** 会话创建时间。 */
    private Instant createdAt;
    /** 会话最近更新时间。 */
    private Instant updatedAt;

}
