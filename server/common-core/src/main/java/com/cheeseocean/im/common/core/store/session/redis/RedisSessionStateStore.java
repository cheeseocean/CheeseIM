package com.cheeseocean.im.common.core.store.session.redis;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.session.SessionStateStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RedisSessionStateStore implements SessionStateStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<String> consumeWsTicketScript;

    public RedisSessionStateStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.consumeWsTicketScript = new DefaultRedisScript<>(consumeWsTicketLua(), String.class);
    }

    @Override
    public void save(SessionPrincipal session, long ttlMs) {
        redisTemplate.opsForValue().set(RedisKeys.userSession(session.getSessionId()), writeJson(session), ttlMs, TimeUnit.MILLISECONDS);
        redisTemplate.opsForSet().add(RedisKeys.userSessions(session.getUserId()), session.getSessionId());
        redisTemplate.expire(RedisKeys.userSessions(session.getUserId()), ttlMs, TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(RedisKeys.deviceSession(session.getUserId(), session.getDeviceId()),
                session.getSessionId(), ttlMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void save(SessionPrincipal session) {
        redisTemplate.opsForValue().set(RedisKeys.userSession(session.getSessionId()), writeJson(session));
    }

    @Override
    public SessionPrincipal findBySessionId(String sessionId) {
        return readJson(redisTemplate.opsForValue().get(RedisKeys.userSession(sessionId)), SessionPrincipal.class);
    }

    @Override
    public List<SessionPrincipal> findByUserId(String userId) {
        Set<String> sessionIds = redisTemplate.opsForSet().members(RedisKeys.userSessions(userId));
        if (sessionIds == null || sessionIds.isEmpty()) {
            return new ArrayList<>();
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
        String sessionId = redisTemplate.opsForValue().get(RedisKeys.deviceSession(userId, deviceId));
        return sessionId == null ? null : findBySessionId(sessionId);
    }

    @Override
    public void saveWsTicket(WsTicketPrincipal ticket, long ttlMs) {
        redisTemplate.opsForValue().set(RedisKeys.wsTicket(ticket.getTicket()), writeJson(ticket), ttlMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public WsTicketPrincipal findWsTicket(String ticket) {
        return readJson(redisTemplate.opsForValue().get(RedisKeys.wsTicket(ticket)), WsTicketPrincipal.class);
    }

    @Override
    public WsTicketPrincipal consumeWsTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String value = redisTemplate.execute(
                consumeWsTicketScript,
                Collections.singletonList(RedisKeys.wsTicket(ticket))
        );
        WsTicketPrincipal principal = readJson(value, WsTicketPrincipal.class);
        if (principal == null) {
            return null;
        }
        principal.setUsed(true);
        return principal;
    }

    private String consumeWsTicketLua() {
        return """
                local key = KEYS[1]
                local value = redis.call('GET', key)
                if not value then
                    return nil
                end
                redis.call('DEL', key)
                return value
                """;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("会话状态序列化失败", exception);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("会话状态反序列化失败", exception);
        }
    }
}
