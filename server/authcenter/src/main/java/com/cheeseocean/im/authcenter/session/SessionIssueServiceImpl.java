package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.common.api.session.SessionIssueService;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.auth.WsTicketPrincipal;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@DubboService
public class SessionIssueServiceImpl implements SessionIssueService {

    private final AccessTokenService accessTokenService;
    private final SessionTicketService sessionTicketService;
    private final RedisTemplate<String, Object> redisTemplate;

    public SessionIssueServiceImpl(AccessTokenService accessTokenService,
                                   SessionTicketService sessionTicketService,
                                   RedisTemplate<String, Object> redisTemplate) {
        this.accessTokenService = accessTokenService;
        this.sessionTicketService = sessionTicketService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public WsTicketPrincipal issueWsTicket(String accessToken, String deviceId, String platform, String clientVersion) {
        AccessTokenPrincipal principal = accessTokenService.validate(accessToken);
        SessionPrincipal session = sessionTicketService.buildSession(principal, deviceId, platform, clientVersion);
        redisTemplate.opsForValue().set(RedisKeys.userSession(session.getSessionId()), session,
                accessTokenService.getTokenExpirationMs(), TimeUnit.MILLISECONDS);
        redisTemplate.opsForSet().add(RedisKeys.userSessions(session.getUserId()), session.getSessionId());
        redisTemplate.expire(RedisKeys.userSessions(session.getUserId()),
                accessTokenService.getTokenExpirationMs(), TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(RedisKeys.deviceSession(session.getUserId(), session.getDeviceId()),
                session.getSessionId(), accessTokenService.getTokenExpirationMs(), TimeUnit.MILLISECONDS);

        WsTicketPrincipal ticket = sessionTicketService.buildTicket(session);
        redisTemplate.opsForValue().set(RedisKeys.wsTicket(ticket.getTicket()), ticket,
                sessionTicketService.wsTicketTtlMs(), TimeUnit.MILLISECONDS);
        return ticket;
    }

    @Override
    public WsTicketPrincipal consumeWsTicket(String ticket) {
        String key = RedisKeys.wsTicket(ticket);
        WsTicketPrincipal principal = (WsTicketPrincipal) redisTemplate.opsForValue().get(key);
        if (principal == null) {
            return null;
        }
        if (principal.isUsed()) {
            return principal;
        }
        principal.setUsed(true);
        long ttl = Math.max(1L, principal.getExpireAt() - System.currentTimeMillis());
        redisTemplate.opsForValue().set(key, principal, ttl, TimeUnit.MILLISECONDS);
        return principal;
    }
}
