package com.cheeseocean.im.authcenter.repository;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import com.cheeseocean.im.common.core.store.session.SessionStateStore;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SessionRepository {

    private final SessionStateStore sessionStateStore;

    public SessionRepository(SessionStateStore sessionStateStore) {
        this.sessionStateStore = sessionStateStore;
    }

    public void save(SessionPrincipal session, long ttlMs) {
        sessionStateStore.save(session, ttlMs);
    }

    public void save(SessionPrincipal session) {
        sessionStateStore.save(session);
    }

    public SessionPrincipal findBySessionId(String sessionId) {
        return sessionStateStore.findBySessionId(sessionId);
    }

    public List<SessionPrincipal> findByUserId(String userId) {
        return sessionStateStore.findByUserId(userId);
    }

    public SessionPrincipal findByDevice(String userId, String deviceId) {
        return sessionStateStore.findByDevice(userId, deviceId);
    }

    public void saveWsTicket(WsTicketPrincipal ticket, long ttlMs) {
        sessionStateStore.saveWsTicket(ticket, ttlMs);
    }

    public WsTicketPrincipal findWsTicket(String ticket) {
        return sessionStateStore.findWsTicket(ticket);
    }
}
