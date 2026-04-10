package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import com.cheeseocean.im.common.api.session.ConnectionAuthService;
import com.cheeseocean.im.common.api.session.SessionIssueService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.session.SessionQueryService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * authcenter 侧的长连接认证实现。
 *
 * @author xxxcrel
 */
@Service
@DubboService
public class ConnectionAuthServiceImpl implements ConnectionAuthService {

    private final SessionIssueService sessionIssueService;
    private final SessionQueryService sessionQueryService;
    private final SessionStateValidator sessionStateValidator;

    public ConnectionAuthServiceImpl(SessionIssueService sessionIssueService,
                                     SessionQueryService sessionQueryService,
                                     SessionStateValidator sessionStateValidator) {
        this.sessionIssueService = sessionIssueService;
        this.sessionQueryService = sessionQueryService;
        this.sessionStateValidator = sessionStateValidator;
    }

    @Override
    public SessionPrincipal authenticateWsTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalStateException("ticket invalid");
        }

        WsTicketPrincipal principal = sessionIssueService.consumeWsTicket(ticket);
        if (principal == null) {
            throw new IllegalStateException("ticket invalid");
        }
        if (principal.isExpired(System.currentTimeMillis())) {
            throw new IllegalStateException("ticket expired");
        }

        SessionPrincipal session = sessionQueryService.getBySessionId(principal.getSessionId());
        sessionStateValidator.validate(session, principal.getTokenVersion(), sessionQueryService);
        return session;
    }
}
