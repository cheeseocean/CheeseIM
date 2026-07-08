package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.authcenter.repository.UserSecurityRepository;
import com.cheeseocean.im.common.api.session.SessionQueryService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class SessionQueryServiceImpl implements SessionQueryService {

    private final SessionRepository sessionRepository;
    private final AccessTokenService accessTokenService;
    private final SessionTicketService sessionTicketService;
    private final UserSecurityRepository userSecurityRepository;

    public SessionQueryServiceImpl(SessionRepository sessionRepository,
                                   AccessTokenService accessTokenService,
                                   SessionTicketService sessionTicketService,
                                   UserSecurityRepository userSecurityRepository) {
        this.sessionRepository = sessionRepository;
        this.accessTokenService = accessTokenService;
        this.sessionTicketService = sessionTicketService;
        this.userSecurityRepository = userSecurityRepository;
    }

    @Override
    public SessionPrincipal getByAccessToken(String accessToken) {
        AccessTokenPrincipal principal = accessTokenService.validate(accessToken);
        validatePrincipalSecurity(principal);
        SessionPrincipal cached = principal.getSessionId() == null ? null : getBySessionId(principal.getSessionId());
        if (cached != null) {
            if (!cached.isActive()) {
                throw new IllegalStateException("session invalid");
            }
            if (!userSecurityRepository.matchesTokenVersion(cached.getUserId(), principal.getTokenVersion())) {
                throw new IllegalStateException("token version mismatch");
            }
            return cached;
        }
        return sessionTicketService.buildSession(principal, principal.getDeviceId(), principal.getPlatform(), null);
    }

    @Override
    public SessionPrincipal getBySessionId(String sessionId) {
        return sessionRepository.findBySessionId(sessionId);
    }

    @Override
    public boolean isSessionValid(String sessionId) {
        SessionPrincipal session = getBySessionId(sessionId);
        return session != null
                && session.isActive()
                && !userSecurityRepository.isBanned(session.getUserId())
                && userSecurityRepository.matchesTokenVersion(session.getUserId(), session.getTokenVersion());
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
            return false;
        }
        return userSecurityRepository.matchesTokenVersion(session.getUserId(), tokenVersion);
    }

    private void validatePrincipalSecurity(AccessTokenPrincipal principal) {
        if (userSecurityRepository.isBanned(principal.getUserId())) {
            throw new IllegalStateException("user banned");
        }
        if (!userSecurityRepository.matchesTokenVersion(principal.getUserId(), principal.getTokenVersion())) {
            throw new IllegalStateException("token version mismatch");
        }
    }
}
