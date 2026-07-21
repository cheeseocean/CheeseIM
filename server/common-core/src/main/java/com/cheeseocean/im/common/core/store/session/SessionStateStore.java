package com.cheeseocean.im.common.core.store.session;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;

import java.util.List;

public interface SessionStateStore {

    void save(SessionPrincipal session, long ttlMs);

    void save(SessionPrincipal session);

    /**
     * 只更新 session 主记录，不改写 user/device 二级索引。
     */
    void updateSession(SessionPrincipal session, long ttlMs);

    SessionPrincipal findBySessionId(String sessionId);

    List<SessionPrincipal> findByUserId(String userId);

    SessionPrincipal findByDevice(String userId, String deviceId);

    void saveWsTicket(WsTicketPrincipal ticket, long ttlMs);

    WsTicketPrincipal findWsTicket(String ticket);

    /**
     * 原子消费一次性长连接 ticket。
     *
     * <p>返回非空表示调用方拿到 ticket 所有权；后续相同 ticket 应返回空，避免 read-then-write 重放窗口。
     */
    WsTicketPrincipal consumeWsTicket(String ticket);
}
