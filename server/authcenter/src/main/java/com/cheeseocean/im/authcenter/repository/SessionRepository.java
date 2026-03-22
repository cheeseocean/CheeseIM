package com.cheeseocean.im.authcenter.repository;

import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
public class SessionRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    public SessionRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(SessionPrincipal session, long ttlMs) {
        redisTemplate.opsForValue().set(RedisKeys.userSession(session.getSessionId()), session, ttlMs, TimeUnit.MILLISECONDS);
        redisTemplate.opsForSet().add(RedisKeys.userSessions(session.getUserId()), session.getSessionId());
        redisTemplate.expire(RedisKeys.userSessions(session.getUserId()), ttlMs, TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(RedisKeys.deviceSession(session.getUserId(), session.getDeviceId()),
                session.getSessionId(), ttlMs, TimeUnit.MILLISECONDS);
    }

    public SessionPrincipal findBySessionId(String sessionId) {
        return (SessionPrincipal) redisTemplate.opsForValue().get(RedisKeys.userSession(sessionId));
    }

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

    public SessionPrincipal findByDevice(String userId, String deviceId) {
        Object sessionId = redisTemplate.opsForValue().get(RedisKeys.deviceSession(userId, deviceId));
        return sessionId == null ? null : findBySessionId(String.valueOf(sessionId));
    }
}
