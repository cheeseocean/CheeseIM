package com.cheeseocean.im.apiserver.auth;

import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 将 HTTP Bearer access token 解析为当前请求对应的会话主体。
 */
@Component
public class CurrentPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    private final AccessTokenSessionResolver accessTokenSessionResolver;

    public CurrentPrincipalArgumentResolver(AccessTokenSessionResolver accessTokenSessionResolver) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return SessionPrincipal.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        return accessTokenSessionResolver.resolve(webRequest.getHeader("Authorization"));
    }
}
