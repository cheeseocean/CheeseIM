package com.cheeseocean.im.apiserver.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * `/api/**` 默认拒绝鉴权拦截器。
 *
 * <p>认证结果写入 request attribute，参数解析器和幂等拦截器复用同一结果，
 * 避免一个请求重复查询 session。</p>
 */
@Component
public class ApiAuthenticationInterceptor implements HandlerInterceptor {

    private final AccessTokenSessionResolver accessTokenSessionResolver;

    public ApiAuthenticationInterceptor(AccessTokenSessionResolver accessTokenSessionResolver) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod) || isPublic(handlerMethod)) {
            return true;
        }
        accessTokenSessionResolver.resolve(request);
        return true;
    }

    private boolean isPublic(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PublicApi.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PublicApi.class);
    }
}
