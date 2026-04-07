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
}
