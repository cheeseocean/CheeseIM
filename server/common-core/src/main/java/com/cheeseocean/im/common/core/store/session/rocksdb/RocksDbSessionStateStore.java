package com.cheeseocean.im.common.core.store.session.rocksdb;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import com.cheeseocean.im.common.core.store.session.SessionStateStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RocksDbSessionStateStore implements SessionStateStore {

    private final RocksDbSupport support;

    public RocksDbSessionStateStore() {
        this(new RocksDbSupport());
    }

    public RocksDbSessionStateStore(java.nio.file.Path dataDirectory, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this(new RocksDbSupport(dataDirectory, objectMapper));
    }

    public RocksDbSessionStateStore(RocksDbSupport support) {
        this.support = support;
    }

    @Override
    public void save(SessionPrincipal session, long ttlMs) {
        Duration ttl = Duration.ofMillis(ttlMs);
        support.put(RedisKeys.userSession(session.getSessionId()), session, ttl);
        support.addToSet(RedisKeys.userSessions(session.getUserId()), session.getSessionId(), ttl);
        support.put(RedisKeys.deviceSession(session.getUserId(), session.getDeviceId()), session.getSessionId(), ttl);
    }

    @Override
    public void save(SessionPrincipal session) {
        support.put(RedisKeys.userSession(session.getSessionId()), session, null);
    }

    @Override
    public SessionPrincipal findBySessionId(String sessionId) {
        return support.get(RedisKeys.userSession(sessionId), SessionPrincipal.class);
    }

    @Override
    public List<SessionPrincipal> findByUserId(String userId) {
        Set<String> sessionIds = support.members(RedisKeys.userSessions(userId));
        if (sessionIds.isEmpty()) {
            return List.of();
        }
        List<SessionPrincipal> sessions = new ArrayList<>();
        for (String sessionId : sessionIds) {
            SessionPrincipal session = findBySessionId(sessionId);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    @Override
    public SessionPrincipal findByDevice(String userId, String deviceId) {
        String sessionId = support.get(RedisKeys.deviceSession(userId, deviceId), String.class);
        return sessionId == null ? null : findBySessionId(String.valueOf(sessionId));
    }

    @Override
    public void saveWsTicket(WsTicketPrincipal ticket, long ttlMs) {
        support.put(RedisKeys.wsTicket(ticket.getTicket()), ticket, Duration.ofMillis(ttlMs));
    }

    @Override
    public WsTicketPrincipal findWsTicket(String ticket) {
        return support.get(RedisKeys.wsTicket(ticket), WsTicketPrincipal.class);
    }
}
