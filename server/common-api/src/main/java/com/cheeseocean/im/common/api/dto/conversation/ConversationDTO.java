package com.cheeseocean.im.common.api.dto.conversation;


import lombok.Data;

/**
 * 用户维度的会话 DTO。
 * 对应写扩散模型下每个参与者独立的一条会话记录。
 */
@Data
public class ConversationDTO {

    /**
     * 会话记录所属用户
     */
    private String ownerUserId;

    /**
     * 会话 ID（如 si_A_B、g_GID）
     */
    private String conversationId;

    /**
     * 会话类型：1=单聊，2=群聊，3=通知
     */
    private int conversationType;

    /**
     * 对端 ID：单聊时为对方 userId，群聊时为 groupId
     */
    private String targetId;

    /**
     * 会话级消息接收选项 code。
     * 取值见 {@link com.cheeseocean.im.common.api.enums.ReceiveOption}。
     */
    private int recvMsgOpt;

    /**
     * 未读消息数
     */
    private int unreadCount;

    /**
     * 最新消息的全局序列号
     */
    private Long latestMsgSeq;

    /**
     * 最新消息摘要（JSON 序列化的 ConversationLastMessageSummary）
     */
    private String latestMsg;

    /**
     * 最后已读的序列号
     */
    private Long readSeq;

    /**
     * 是否置顶
     */
    private boolean pinned;

    /**
     * 草稿文本
     */
    private String draftText;

    /**
     * 附加信息（扩展字段）
     */
    private String attachedInfo;

    /**
     * 创建时间（毫秒时间戳）
     */
    private long createdAt;

    /**
     * 最后更新时间（毫秒时间戳）
     */
    private long updatedAt;
}
