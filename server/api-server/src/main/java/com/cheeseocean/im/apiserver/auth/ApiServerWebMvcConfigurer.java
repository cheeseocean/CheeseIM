package com.cheeseocean.im.apiserver.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.cheeseocean.im.apiserver.interceptor.ApiIdempotencyInterceptor;

import java.util.List;

/**
 * 注册 API Server 侧的 MVC 参数解析器。
 */
@Configuration
public class ApiServerWebMvcConfigurer implements WebMvcConfigurer {

    private final CurrentPrincipalArgumentResolver currentPrincipalArgumentResolver;
    private final ApiAuthenticationInterceptor apiAuthenticationInterceptor;
    private final ApiIdempotencyInterceptor apiIdempotencyInterceptor;

    public ApiServerWebMvcConfigurer(CurrentPrincipalArgumentResolver currentPrincipalArgumentResolver,
                                     ApiAuthenticationInterceptor apiAuthenticationInterceptor,
                                     ApiIdempotencyInterceptor apiIdempotencyInterceptor) {
        this.currentPrincipalArgumentResolver = currentPrincipalArgumentResolver;
        this.apiAuthenticationInterceptor = apiAuthenticationInterceptor;
        this.apiIdempotencyInterceptor = apiIdempotencyInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentPrincipalArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiAuthenticationInterceptor)
                .addPathPatterns("/api/**")
                .order(-100);
        registry.addInterceptor(apiIdempotencyInterceptor).addPathPatterns("/api/**");
    }
}
