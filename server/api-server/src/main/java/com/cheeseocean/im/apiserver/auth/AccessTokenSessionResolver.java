package com.cheeseocean.im.apiserver.auth;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import com.cheeseocean.im.common.api.session.SessionQueryService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 解析 HTTP Bearer access token，并映射成当前会话主体。
 */
@Component("socialAccessTokenSessionResolver")
public class AccessTokenSessionResolver {

    public static final String REQUEST_PRINCIPAL_ATTRIBUTE = AccessTokenSessionResolver.class.getName() + ".principal";

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

    /**
     * 解析并缓存当前请求主体，供幂等拦截器与 Controller 参数解析器共享。
     */
    public SessionPrincipal resolve(HttpServletRequest request) {
        Object cached = request.getAttribute(REQUEST_PRINCIPAL_ATTRIBUTE);
        if (cached instanceof SessionPrincipal principal) {
            return principal;
        }
        SessionPrincipal principal = resolve(request.getHeader("Authorization"));
        request.setAttribute(REQUEST_PRINCIPAL_ATTRIBUTE, principal);
        return principal;
    }
}
