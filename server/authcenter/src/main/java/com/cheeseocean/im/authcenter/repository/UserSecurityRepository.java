package com.cheeseocean.im.authcenter.repository;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserSecurityRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    public UserSecurityRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isBanned(String userId) {
        Object value = redisTemplate.opsForHash().get(RedisKeys.userSecurity(userId), "banned");
        return Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value));
    }

    public void setBanned(String userId, boolean banned) {
        redisTemplate.opsForHash().put(RedisKeys.userSecurity(userId), "banned", banned);
    }
}
