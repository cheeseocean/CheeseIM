package com.cheeseocean.im.postoffice.auth;

import com.cheeseocean.im.common.api.session.SessionQueryService;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Component
public class ConnectionSessionGuard {

    @DubboReference(check = false, retries = 0)
    private SessionQueryService sessionQueryDubboService;

    private final long validationIntervalMillis;

    public ConnectionSessionGuard(ServerProperties serverProperties) {
        this.validationIntervalMillis = Math.max(
                5_000L, serverProperties.getSessionValidation().getIntervalMs());
    }

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
        long now = System.currentTimeMillis();
        if (now - context.getSessionValidatedAt() < validationIntervalMillis) {
            return;
        }

        // 同一连接的业务任务可能并发到达，只允许一个线程在租约到期时回源 authcenter。
        synchronized (connection) {
            now = System.currentTimeMillis();
            if (now - context.getSessionValidatedAt() < validationIntervalMillis) {
                return;
            }
            // isSessionValid 已同时校验 active、ban 与当前 tokenVersion，无需再调用 matchesTokenVersion。
            if (!sessionQueryDubboService.isSessionValid(context.getSessionId())) {
                throw new IllegalStateException("session invalid");
            }
            context.setSessionValidatedAt(now);
        }
    }
}
