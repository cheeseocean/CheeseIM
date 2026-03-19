package com.cheeseocean.im.postoffice.auth;

import com.cheeseocean.im.common.api.session.SessionIssueService;
import com.cheeseocean.im.common.api.session.SessionQueryService;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import com.cheeseocean.im.common.model.auth.WsTicketPrincipal;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

@Service
public class WsTicketAuthServiceImpl implements WsTicketAuthService {

    @DubboReference(check = false)
    private SessionIssueService sessionIssueService;

    @DubboReference(check = false)
    private SessionQueryService sessionQueryService;

    private final SessionStateValidator sessionStateValidator;

    public WsTicketAuthServiceImpl(SessionStateValidator sessionStateValidator) {
        this.sessionStateValidator = sessionStateValidator;
    }

    @Override
    public SessionPrincipal authenticate(String ticket) {
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
