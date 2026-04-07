package com.cheeseocean.im.postoffice.auth;

import com.cheeseocean.im.common.api.session.SessionQueryService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.springframework.stereotype.Component;

@Component
public class SessionStateValidator {

    public void validate(SessionPrincipal session, Long tokenVersion, SessionQueryService sessionQueryDubboService) {
        if (session == null || !session.isActive()) {
            throw new IllegalStateException("session invalid");
        }
        if (sessionQueryDubboService.isUserBanned(session.getUserId())) {
            throw new IllegalStateException("user banned");
        }
        if (tokenVersion != null && !sessionQueryDubboService.matchesTokenVersion(session.getSessionId(), tokenVersion)) {
            throw new IllegalStateException("token version mismatch");
        }
    }
}
