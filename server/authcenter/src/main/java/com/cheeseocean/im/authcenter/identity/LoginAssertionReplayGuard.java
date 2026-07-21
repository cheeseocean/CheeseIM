package com.cheeseocean.im.authcenter.identity;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 使用 Redis NX 消费登录断言 jti，阻断有效期内的重复登录重放。
 */
@Component
public class LoginAssertionReplayGuard {

    private final StringRedisTemplate redisTemplate;

    public LoginAssertionReplayGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 原子消费 jti。Redis 不可用时异常上抛并拒绝登录，认证链路不做 fail-open。
     */
    public boolean consume(String jti, long ttlMs) {
        String key = RedisKeys.loginAssertionReplay(sha256(jti));
        Boolean accepted = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofMillis(Math.max(1L, ttlMs)));
        return Boolean.TRUE.equals(accepted);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
