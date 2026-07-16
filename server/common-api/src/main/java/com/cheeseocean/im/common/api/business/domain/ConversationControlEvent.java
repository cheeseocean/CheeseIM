package com.cheeseocean.im.common.api.business.domain;

import com.cheeseocean.im.common.api.enums.ControlEventDeliveryStateEnum;
import com.cheeseocean.im.common.api.enums.ControlEventTypeEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 会话控制事件。
 *
 * <p>这是已读、撤回和瞬时输入状态共享的可靠通知载体。{@code cursor} 是服务端分配的
 * 用户维度稳定游标；客户端按目标用户拉取时只需保存该值，不能跨用户复用，也不能以 createdAt 代替。
 */
@Data
public class ConversationControlEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 稳定事件 ID，同时是 outbox 主键。 */
    private String eventId;
    /** 可恢复同步使用的用户维度单调游标。 */
    private long cursor;
    /** 控制事件所属会话。 */
    private String conversationId;
    /** 控制事件类型。 */
    private ControlEventTypeEnum type;
    /** 需要看到该事件的用户集合。 */
    private List<String> targetUserIds;
    /** 类型化事件载荷的 JSON 表示；协议编码由投递适配器负责。 */
    private String payload;
    /** 创建时间，毫秒时间戳。 */
    private long createdAt;
    /** 事件失效时间，毫秒时间戳；到期后不再投递或返回同步流。 */
    private long expiresAt;
    /** outbox 投递状态。 */
    private ControlEventDeliveryStateEnum deliveryState;
    /** 已领取或重试的次数。 */
    private int deliveryAttempt;
    /** 当前领取租约令牌，仅 claim 成功后返回给投递器。 */
    private String claimToken;
    /** 当前领取租约到期时间，毫秒时间戳。 */
    private long claimExpiresAt;
    /** 最终投递完成时间，毫秒时间戳。 */
    private long deliveredAt;
}
