package com.cheeseocean.im.common.api.business.domain;

import com.cheeseocean.im.common.api.enums.ReceiveOption;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户-会话业务状态领域对象（写扩散模型）。
 *
 * <p>每位参与者拥有独立的一条记录，存储会话的个性化配置（置顶、免打扰、草稿等）
 * 及会话状态信息（未读计数等）。
 *
 * <p>序列号相关字段（maxSeq / minSeq / readSeq）独立存储在偏移量对象中，
 * 避免高频已读回执写入污染本表。
 */
@Data
public class UserConversation implements Serializable {

    /**
     * 会话所属者用户 ID
     */
    private String  ownerUserId;
    /**
     * 会话唯一标识（如 si_{A}_{B} 或 sg_{groupId}）
     */
    private String  conversationId;
    /**
     * 会话类型：1=单聊，2=普通群聊，3=通知
     */
    private int     chatType;
    /**
     * 单聊对端用户 ID 或群聊的 groupId
     */
    private String  targetId;
    /**
     * 免打扰开关。
     * 0=正常接收，1=不收消息，2=收不提醒。
     * 取值见 {@link ReceiveOption}。
     */
    private int     receiveOpt;
    /**
     * 当前未读消息数（由消息投递增量维护，标记已读时归零）
     */
    private int     unreadCount;
    /**
     * 是否置顶该会话
     */
    private boolean pinned;
    /**
     * 强提醒元数据（JSON 字符串）
     */
    private String  attachedInfo;
    /**
     * 群 @ 强提醒类型。
     * 取值见 {@link com.cheeseocean.im.common.api.enums.GroupAtTypeEnum}。
     */
    private int     groupAtType;
    /**
     * 是否开启消息自动清理
     */
    private boolean autoCleanup;
    /**
     * 消息自动清理周期（秒）
     */
    private long    cleanupCycle;
    /**
     * 最近一次执行消息清理的时间（毫秒时间戳）
     */
    private long    latestCleanupTime;
    /**
     * 会话首次激活时间（毫秒时间戳）
     */
    private long    createdAt;
    /**
     * 最近更新时间（毫秒时间戳）
     */
    private long    updatedAt;
}
