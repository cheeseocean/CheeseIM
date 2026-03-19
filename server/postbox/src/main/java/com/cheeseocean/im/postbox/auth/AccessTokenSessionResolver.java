package com.cheeseocean.im.postbox.auth;

import com.cheeseocean.im.common.api.session.SessionQueryService;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AccessTokenSessionResolver {

    @DubboReference(check = false)
    private SessionQueryService sessionQueryDubboService;

    public SessionPrincipal resolve(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new IllegalStateException("access token missing");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        SessionPrincipal session = sessionQueryDubboService.getByAccessToken(token);
        if (session == null || !session.isActive()) {
            throw new IllegalStateException("session invalid");
        }
        return session;
    }
}
