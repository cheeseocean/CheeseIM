package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.authcenter.auth.AccessTokenPrincipal;
import com.cheeseocean.im.authcenter.auth.AccessTokenService;
import com.cheeseocean.im.authcenter.repository.SessionRepository;
import com.cheeseocean.im.common.api.session.SessionIssueService;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.auth.WsTicketPrincipal;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class SessionIssueServiceImpl implements SessionIssueService {

    private final AccessTokenService accessTokenService;
    private final SessionTicketService sessionTicketService;
    private final SessionRepository sessionRepository;

    public SessionIssueServiceImpl(AccessTokenService accessTokenService,
                                   SessionTicketService sessionTicketService,
                                   SessionRepository sessionRepository) {
        this.accessTokenService = accessTokenService;
        this.sessionTicketService = sessionTicketService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public WsTicketPrincipal issueWsTicket(String accessToken, String deviceId, String platform, String clientVersion) {
        AccessTokenPrincipal principal = accessTokenService.validate(accessToken);
        SessionPrincipal session = sessionTicketService.buildSession(principal, deviceId, platform, clientVersion);
        sessionRepository.save(session, accessTokenService.getTokenExpirationMs());

        WsTicketPrincipal ticket = sessionTicketService.buildTicket(session);
        sessionRepository.saveWsTicket(ticket, sessionTicketService.wsTicketTtlMs());
        return ticket;
    }

    @Override
    public WsTicketPrincipal consumeWsTicket(String ticket) {
        WsTicketPrincipal principal = sessionRepository.findWsTicket(ticket);
        if (principal == null) {
            return null;
        }
        if (principal.isUsed()) {
            return principal;
        }
        principal.setUsed(true);
        long ttl = Math.max(1L, principal.getExpireAt() - System.currentTimeMillis());
        sessionRepository.saveWsTicket(principal, ttl);
        return principal;
    }
}
