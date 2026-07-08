package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.authcenter.repository.UserSecurityRepository;
import com.cheeseocean.im.common.api.session.SessionIssueService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class SessionIssueServiceImpl implements SessionIssueService {

    private final AccessTokenService accessTokenService;
    private final SessionTicketService sessionTicketService;
    private final SessionRepository sessionRepository;
    private final UserSecurityRepository userSecurityRepository;

    public SessionIssueServiceImpl(AccessTokenService accessTokenService,
                                   SessionTicketService sessionTicketService,
                                   SessionRepository sessionRepository,
                                   UserSecurityRepository userSecurityRepository) {
        this.accessTokenService = accessTokenService;
        this.sessionTicketService = sessionTicketService;
        this.sessionRepository = sessionRepository;
        this.userSecurityRepository = userSecurityRepository;
    }

    @Override
    public WsTicketPrincipal issueWsTicket(String accessToken, String deviceId, String platform, String clientVersion) {
        AccessTokenPrincipal principal = accessTokenService.validate(accessToken);
        if (userSecurityRepository.isBanned(principal.getUserId())) {
            throw new IllegalStateException("user banned");
        }
        if (!userSecurityRepository.matchesTokenVersion(principal.getUserId(), principal.getTokenVersion())) {
            throw new IllegalStateException("token version mismatch");
        }
        SessionPrincipal session = resolveSession(principal, deviceId, platform, clientVersion);
        sessionRepository.save(session, accessTokenService.getTokenExpirationMs());

        WsTicketPrincipal ticket = sessionTicketService.buildTicket(session);
        sessionRepository.saveWsTicket(ticket, sessionTicketService.wsTicketTtlMs());
        return ticket;
    }

    @Override
    public WsTicketPrincipal consumeWsTicket(String ticket) {
        return sessionRepository.consumeWsTicket(ticket);
    }

    private SessionPrincipal resolveSession(AccessTokenPrincipal principal, String deviceId, String platform, String clientVersion) {
        if (principal.getSessionId() == null || principal.getSessionId().isBlank()) {
            return sessionTicketService.buildSession(principal, deviceId, platform, clientVersion);
        }
        SessionPrincipal session = sessionRepository.findBySessionId(principal.getSessionId());
        if (session == null || !session.isActive()) {
            throw new IllegalStateException("session invalid");
        }
        if (!userSecurityRepository.matchesTokenVersion(session.getUserId(), principal.getTokenVersion())) {
            throw new IllegalStateException("token version mismatch");
        }
        return session;
    }
}
