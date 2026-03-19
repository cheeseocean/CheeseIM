package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.authcenter.repository.UserSecurityRepository;
import com.cheeseocean.im.common.api.session.SessionQueryService;
import com.cheeseocean.im.common.constants.RedisKeys;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class SessionQueryServiceImpl implements SessionQueryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AccessTokenService accessTokenService;
    private final SessionTicketService sessionTicketService;
    private final UserSecurityRepository userSecurityRepository;

    public SessionQueryServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                   AccessTokenService accessTokenService,
                                   SessionTicketService sessionTicketService,
                                   UserSecurityRepository userSecurityRepository) {
        this.redisTemplate = redisTemplate;
        this.accessTokenService = accessTokenService;
        this.sessionTicketService = sessionTicketService;
        this.userSecurityRepository = userSecurityRepository;
    }

    @Override
    public SessionPrincipal getByAccessToken(String accessToken) {
        AccessTokenPrincipal principal = accessTokenService.validate(accessToken);
        SessionPrincipal cached = getBySessionId("sess:" + principal.getUserId() + ":" + principal.getDeviceId());
        if (cached != null) {
            return cached;
        }
        return sessionTicketService.buildSession(principal, principal.getDeviceId(), principal.getPlatform(), null);
    }

    @Override
    public SessionPrincipal getBySessionId(String sessionId) {
        return (SessionPrincipal) redisTemplate.opsForValue().get(RedisKeys.USER_SESSION + sessionId);
    }

    @Override
    public boolean isSessionValid(String sessionId) {
        SessionPrincipal session = getBySessionId(sessionId);
        return session != null && session.isActive();
    }

    @Override
    public boolean isUserBanned(String userId) {
        return userSecurityRepository.isBanned(userId);
    }

    @Override
    public boolean matchesTokenVersion(String sessionId, Long tokenVersion) {
        SessionPrincipal session = getBySessionId(sessionId);
        if (session == null) {
            return false;
        }
        if (tokenVersion == null) {
            return true;
        }
        return tokenVersion.equals(session.getTokenVersion());
    }
}
