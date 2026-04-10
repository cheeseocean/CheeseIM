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

    /**
     * 仅校验连接本地状态是否已完成认证且上下文完整。
     */
    public void ensureAuthenticated(UserConnection connection) {
        if (connection == null || !connection.isAuthenticated()) {
            throw new IllegalStateException("connection unauthenticated");
        }

        ConnectionContext context = connection.getContext();
        if (context == null || context.getSessionId() == null || context.getSessionId().isBlank()) {
            throw new IllegalStateException("connection context invalid");
        }
        if (!context.isAuthenticated()) {
            throw new IllegalStateException("connection context invalid");
        }
    }

    /**
     * 校验连接绑定的 session 在服务端是否仍然有效。
     */
    public void ensureSessionActive(UserConnection connection) {
        ensureAuthenticated(connection);
        ConnectionContext context = connection.getContext();
        if (!sessionQueryDubboService.isSessionValid(context.getSessionId())) {
            throw new IllegalStateException("session invalid");
        }

        if (context.getTokenVersion() != null
                && !sessionQueryDubboService.matchesTokenVersion(context.getSessionId(), context.getTokenVersion())) {
            throw new IllegalStateException("token version mismatch");
        }
    }
}
