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
        sessionStateStore.save(session, remainingTtl(session));
    }

    public void updateSession(SessionPrincipal session) {
        sessionStateStore.updateSession(session, remainingTtl(session));
    }

    public void updateSession(SessionPrincipal session, long ttlMs) {
        sessionStateStore.updateSession(session, ttlMs);
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

    public WsTicketPrincipal consumeWsTicket(String ticket) {
        return sessionStateStore.consumeWsTicket(ticket);
    }

    private long remainingTtl(SessionPrincipal session) {
        Long expireAt = session.getRefreshTokenExpireAt();
        if (expireAt == null) {
            throw new IllegalStateException("session refresh lifetime required");
        }
        long remainingTtlMs = expireAt - System.currentTimeMillis();
        if (remainingTtlMs <= 0) {
            throw new IllegalStateException("session refresh lifetime expired");
        }
        return remainingTtlMs;
    }
}
