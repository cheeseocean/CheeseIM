package com.cheeseocean.im.postoffice.auth;

import com.cheeseocean.im.common.api.session.SessionQueryService;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Component
public class ConnectionSessionGuard {

    @DubboReference(check = false)
    private SessionQueryService sessionQueryDubboService;

    public void ensureValid(UserConnection connection) {
        if (connection == null || !connection.isAuthenticated()) {
            throw new IllegalStateException("connection unauthenticated");
        }

        ConnectionContext context = connection.getContext();
        if (context == null || context.getSessionId() == null || context.getSessionId().isBlank()) {
            throw new IllegalStateException("connection context invalid");
        }

        if (!sessionQueryDubboService.isSessionValid(context.getSessionId())) {
            throw new IllegalStateException("session invalid");
        }

        if (context.getTokenVersion() != null
                && !sessionQueryDubboService.matchesTokenVersion(context.getSessionId(), context.getTokenVersion())) {
            throw new IllegalStateException("token version mismatch");
        }
    }
}
