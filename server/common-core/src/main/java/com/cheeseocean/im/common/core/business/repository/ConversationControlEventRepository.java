package com.cheeseocean.im.common.core.business.repository;

import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;

import java.util.List;
import java.util.Optional;

/**
 * 会话控制事件 outbox 的深模块接口。
 *
 * <p>该接口集中稳定游标分配、TTL、领取租约和投递确认；业务模块只追加事件，投递器只领取并确认，
 * 不得自行维护状态或重试字段。
 */
public interface ConversationControlEventRepository {

    /**
     * 追加待投递控制事件，并填充 eventId、cursor、创建时间和初始状态。
     *
     * <p>expiresAt 必须由调用方按事件语义给出：输入中通常为秒级，已读和撤回应覆盖客户端同步窗口。
     */
    ConversationControlEvent append(ConversationControlEvent event);

    /**
     * 按用户和稳定游标读取未过期控制事件，结果按 cursor 升序排列。
     */
    List<ConversationControlEvent> findAfter(String targetUserId, long cursor, int limit);

    /**
     * 扫描可领取的未过期事件，包含 PENDING 与领取租约已过期的 CLAIMED 事件。
     *
     * <p>调用方仍须逐条 {@link #claim(String, long)}，以保证多副本投递器间的原子竞争。
     */
    List<ConversationControlEvent> findClaimable(int limit);

    /**
     * 原子领取一个待投递事件；已过期租约可由新的投递器接管。
     *
     * @param claimLeaseMillis 领取租约时长，必须大于零
     */
    Optional<ConversationControlEvent> claim(String eventId, long claimLeaseMillis);

    /**
     * 使用 claim 返回的 token 原子确认投递完成，过期领取者不得确认新的领取。
     */
    boolean markDelivered(String eventId, String claimToken);
}
