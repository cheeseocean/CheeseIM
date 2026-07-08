package com.cheeseocean.im.authcenter.session;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.session.SessionQueryService;
import org.springframework.stereotype.Component;

/**
 * 校验 session 在长连接接入时是否仍然有效。
 *
 * @author xxxcrel
 */
@Component
public class SessionStateValidator {

    public void validate(SessionPrincipal session, Long tokenVersion, SessionQueryService sessionQueryService) {
        if (session == null || !session.isActive()) {
            throw new IllegalStateException("session invalid");
        }
        if (sessionQueryService.isUserBanned(session.getUserId())) {
            throw new IllegalStateException("user banned");
        }
        if (tokenVersion == null || !sessionQueryService.matchesTokenVersion(session.getSessionId(), tokenVersion)) {
            throw new IllegalStateException("token version mismatch");
        }
    }
}
