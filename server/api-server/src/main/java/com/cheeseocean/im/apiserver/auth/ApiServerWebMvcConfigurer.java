package com.cheeseocean.im.apiserver.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 注册 API Server 侧的 MVC 参数解析器。
 */
@Configuration
public class ApiServerWebMvcConfigurer implements WebMvcConfigurer {

    private final CurrentPrincipalArgumentResolver currentPrincipalArgumentResolver;

    public ApiServerWebMvcConfigurer(CurrentPrincipalArgumentResolver currentPrincipalArgumentResolver) {
        this.currentPrincipalArgumentResolver = currentPrincipalArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentPrincipalArgumentResolver);
    }
}
