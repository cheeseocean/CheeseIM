package com.cheeseocean.im.apiserver.filter;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Collections;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP API 跨副本固定窗口限流。
 *
 * <p>默认只使用 TCP peer 地址，避免直接信任客户端伪造的转发头。部署在受控代理后时，
 * 必须显式配置可信代理跳数后才解析 {@code X-Forwarded-For}。</p>
 *
 * <p>Redis 故障时按入口可用性策略 fail-open，但记录 unavailable 指标；边缘网关仍应提供
 * 独立的连接数、带宽和 DDoS 防护，本过滤器只承担应用层第二道限流。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('INCR', KEYS[1])
                    if current == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return current
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final boolean enabled;
    private final long requestsPerWindow;
    private final long windowMillis;
    private final int trustedProxyHops;
    private final long failOpenRetryMillis;
    private final AtomicLong redisUnavailableUntil = new AtomicLong();
    private final AtomicBoolean recoveryProbeInProgress = new AtomicBoolean();

    public ApiRateLimitFilter(
            StringRedisTemplate redisTemplate,
            @Value("${cheeseim.api.rate-limit.enabled:true}") boolean enabled,
            @Value("${cheeseim.api.rate-limit.requests-per-window:120}") long requestsPerWindow,
            @Value("${cheeseim.api.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${cheeseim.api.rate-limit.trusted-proxy-hops:0}") int trustedProxyHops,
            @Value("${cheeseim.api.rate-limit.fail-open-retry-millis:1000}") long failOpenRetryMillis) {
        this(redisTemplate,
                Clock.systemUTC(),
                enabled,
                requestsPerWindow,
                windowSeconds,
                trustedProxyHops,
                failOpenRetryMillis);
    }

    ApiRateLimitFilter(
            StringRedisTemplate redisTemplate,
            Clock clock,
            boolean enabled,
            long requestsPerWindow,
            long windowSeconds,
            int trustedProxyHops,
            long failOpenRetryMillis) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.enabled = enabled;
        this.requestsPerWindow = Math.min(1_000_000_000L, Math.max(1L, requestsPerWindow));
        this.windowMillis = Math.min(86_400L, Math.max(1L, windowSeconds)) * 1_000L;
        this.trustedProxyHops = Math.min(10, Math.max(0, trustedProxyHops));
        this.failOpenRetryMillis = Math.min(60_000L, Math.max(100L, failOpenRetryMillis));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = clock.millis();
        long unavailableUntil = redisUnavailableUntil.get();
        if (now < unavailableUntil) {
            ImMetrics.apiRateLimit("unavailable");
            filterChain.doFilter(request, response);
            return;
        }
        boolean recoveryProbe = unavailableUntil > 0L;
        if (recoveryProbe && !recoveryProbeInProgress.compareAndSet(false, true)) {
            ImMetrics.apiRateLimit("unavailable");
            filterChain.doFilter(request, response);
            return;
        }
        long windowBucket = now / windowMillis;
        String key = RedisKeys.apiRateLimit(
                fingerprint(resolveClientAddress(request)),
                windowBucket);
        Long current;
        try {
            current = redisTemplate.execute(
                    INCREMENT_SCRIPT,
                    Collections.singletonList(key),
                    Long.toString(windowMillis + 1_000L));
        } catch (RuntimeException redisUnavailable) {
            markUnavailable(now);
            filterChain.doFilter(request, response);
            return;
        } finally {
            if (recoveryProbe) {
                recoveryProbeInProgress.set(false);
            }
        }
        if (current == null) {
            markUnavailable(now);
            filterChain.doFilter(request, response);
            return;
        }
        redisUnavailableUntil.set(0L);
        if (current <= requestsPerWindow) {
            ImMetrics.apiRateLimit("allowed");
            filterChain.doFilter(request, response);
            return;
        }
        ImMetrics.apiRateLimit("rejected");
        writeTooManyRequests(response, now);
    }

    private void markUnavailable(long now) {
        redisUnavailableUntil.accumulateAndGet(now + failOpenRetryMillis, Math::max);
        ImMetrics.apiRateLimit("unavailable");
    }

    private String resolveClientAddress(HttpServletRequest request) {
        if (trustedProxyHops > 0) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String[] chain = forwardedFor.split(",");
                int clientIndex = chain.length - trustedProxyHops;
                if (clientIndex >= 0 && clientIndex < chain.length) {
                    String candidate = chain[clientIndex].trim();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
            }
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private void writeTooManyRequests(HttpServletResponse response, long now) throws IOException {
        long remainingMillis = windowMillis - Math.floorMod(now, windowMillis);
        long retryAfterSeconds = Math.max(1L, (remainingMillis + 999L) / 1_000L);
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":42900,\"message\":\"请求过于频繁\"}");
    }

    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 不可用", impossible);
        }
    }
}
