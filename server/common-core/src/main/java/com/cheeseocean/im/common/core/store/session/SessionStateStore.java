package com.cheeseocean.im.common.core.store.session;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;

import java.util.List;

public interface SessionStateStore {

    void save(SessionPrincipal session, long ttlMs);

    void save(SessionPrincipal session);

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
