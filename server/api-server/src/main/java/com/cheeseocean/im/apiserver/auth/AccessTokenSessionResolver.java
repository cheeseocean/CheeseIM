package com.cheeseocean.im.apiserver.auth;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.session.SessionQueryService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析 HTTP Bearer access token，并映射成当前会话主体。
 */
@Component("socialAccessTokenSessionResolver")
public class AccessTokenSessionResolver {

    @DubboReference(check = false)
    private SessionQueryService sessionQueryService;

    public SessionPrincipal resolve(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new IllegalStateException("access token missing");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        SessionPrincipal session = sessionQueryService.getByAccessToken(token);
        if (session == null || !session.isActive()) {
            throw new IllegalStateException("session invalid");
        }
        return session;
    }
}
