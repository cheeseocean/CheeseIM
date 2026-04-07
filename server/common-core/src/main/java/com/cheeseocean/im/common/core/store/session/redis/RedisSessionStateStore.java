package com.cheeseocean.im.common.core.store.session.redis;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.session.SessionStateStore;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RedisSessionStateStore implements SessionStateStore {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisSessionStateStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(SessionPrincipal session, long ttlMs) {
        redisTemplate.opsForValue().set(RedisKeys.userSession(session.getSessionId()), session, ttlMs, TimeUnit.MILLISECONDS);
        redisTemplate.opsForSet().add(RedisKeys.userSessions(session.getUserId()), session.getSessionId());
        redisTemplate.expire(RedisKeys.userSessions(session.getUserId()), ttlMs, TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(RedisKeys.deviceSession(session.getUserId(), session.getDeviceId()),
                session.getSessionId(), ttlMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void save(SessionPrincipal session) {
        redisTemplate.opsForValue().set(RedisKeys.userSession(session.getSessionId()), session);
    }

    @Override
    public SessionPrincipal findBySessionId(String sessionId) {
        return (SessionPrincipal) redisTemplate.opsForValue().get(RedisKeys.userSession(sessionId));
    }

    @Override
    public List<SessionPrincipal> findByUserId(String userId) {
        Set<Object> sessionIds = redisTemplate.opsForSet().members(RedisKeys.userSessions(userId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        List<SessionPrincipal> sessions = new ArrayList<>();
        for (Object sessionId : sessionIds) {
            SessionPrincipal session = findBySessionId(String.valueOf(sessionId));
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    @Override
    public SessionPrincipal findByDevice(String userId, String deviceId) {
        Object sessionId = redisTemplate.opsForValue().get(RedisKeys.deviceSession(userId, deviceId));
        return sessionId == null ? null : findBySessionId(String.valueOf(sessionId));
    }

    @Override
    public void saveWsTicket(WsTicketPrincipal ticket, long ttlMs) {
        redisTemplate.opsForValue().set(RedisKeys.wsTicket(ticket.getTicket()), ticket, ttlMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public WsTicketPrincipal findWsTicket(String ticket) {
        return (WsTicketPrincipal) redisTemplate.opsForValue().get(RedisKeys.wsTicket(ticket));
    }
}
