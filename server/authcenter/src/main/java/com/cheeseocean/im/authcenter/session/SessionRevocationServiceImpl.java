package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.common.api.connection.KickoffCommandDubboService;
import com.cheeseocean.im.common.api.session.SessionRevocationService;
import com.cheeseocean.im.common.constants.RedisKeys;
import com.cheeseocean.im.common.enums.SessionStatus;
import com.cheeseocean.im.common.model.auth.KickoffCommand;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@DubboService
public class SessionRevocationServiceImpl implements SessionRevocationService {

    private final RedisTemplate<String, Object> redisTemplate;

    @DubboReference(check = false)
    private KickoffCommandDubboService kickoffCommandDubboService;

    public SessionRevocationServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void revokeSession(String sessionId, String reason) {
        SessionPrincipal session = (SessionPrincipal) redisTemplate.opsForValue().get(RedisKeys.USER_SESSION + sessionId);
        if (session == null) {
            return;
        }
        session.setStatus(SessionStatus.REVOKED);
        redisTemplate.opsForValue().set(RedisKeys.USER_SESSION + sessionId, session);

        KickoffCommand command = new KickoffCommand();
        command.setSessionId(sessionId);
        command.setReason(reason);
        kickoffCommandDubboService.kickoffBySession(command);
    }

    @Override
    public void revokeUserSessions(String userId, String reason) {
        Set<Object> sessionIds = redisTemplate.opsForSet().members(RedisKeys.USER_SESSIONS + userId);
        if (sessionIds == null) {
            return;
        }
        for (Object sessionId : sessionIds) {
            revokeSession(String.valueOf(sessionId), reason);
        }
    }

    @Override
    public void revokeDeviceSession(String userId, String deviceId, String reason) {
        Object sessionId = redisTemplate.opsForValue().get(RedisKeys.DEVICE_SESSION + userId + ":" + deviceId);
        if (sessionId == null) {
            return;
        }
        revokeSession(String.valueOf(sessionId), reason);
    }
}
