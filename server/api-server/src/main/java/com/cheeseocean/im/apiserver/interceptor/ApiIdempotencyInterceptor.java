package com.cheeseocean.im.apiserver.interceptor;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.idempotency.IdempotencyStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * 为声明当前会话主体的 HTTP 写接口提供可选的跨副本幂等占位。
 *
 * <p>只在客户端明确携带 {@code Idempotency-Key} 时生效。首次请求仅占位而不缓存响应，
 * 因此同一 key 的后续请求统一返回冲突，避免在网络重试时重复执行写操作。
 */
@Component
public class ApiIdempotencyInterceptor implements HandlerInterceptor {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final IdempotencyStore idempotencyStore;
    private final boolean enabled;
    private final Duration ttl;

    public ApiIdempotencyInterceptor(AccessTokenSessionResolver accessTokenSessionResolver,
                                     IdempotencyStore idempotencyStore,
                                     @Value("${cheeseim.api.idempotency.enabled:true}") boolean enabled,
                                     @Value("${cheeseim.api.idempotency.ttl-seconds:300}") long ttlSeconds) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.idempotencyStore = idempotencyStore;
        this.enabled = enabled;
        this.ttl = Duration.ofSeconds(Math.max(1L, ttlSeconds));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!enabled || !isWriteMethod(request.getMethod()) || !hasSessionPrincipal(handler)) {
            return true;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (!StringUtils.hasText(idempotencyKey)) {
            return true;
        }

        SessionPrincipal principal = accessTokenSessionResolver.resolve(request);
        String redisKey = RedisKeys.apiIdempotency(
                principal.getUserId(),
                request.getMethod(),
                request.getRequestURI(),
                fingerprint(idempotencyKey));
        if (idempotencyStore.putIfAbsent(redisKey, ttl)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":40901,\"message\":\"请求正在处理或已处理\"}");
        return false;
    }

    private boolean isWriteMethod(String method) {
        return HttpMethod.POST.matches(method)
                || HttpMethod.PUT.matches(method)
                || HttpMethod.DELETE.matches(method);
    }

    private boolean hasSessionPrincipal(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        for (var parameter : handlerMethod.getMethodParameters()) {
            if (SessionPrincipal.class.isAssignableFrom(parameter.getParameterType())) {
                return true;
            }
        }
        return false;
    }

    private String fingerprint(String idempotencyKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
