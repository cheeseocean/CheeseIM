package com.cheeseocean.im.authcenter.repository;

import com.cheeseocean.im.common.core.cache.MultiLevelCacheService;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.springframework.stereotype.Repository;

@Repository
public class UserSecurityRepository {

    private final MultiLevelCacheService cacheService;

    public UserSecurityRepository(MultiLevelCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public boolean isBanned(String userId) {
        Object value = cacheService.getOrLoad(RedisKeys.userSecurity(userId) + ":banned", String.class, java.time.Duration.ofDays(7), () -> null);
        return Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value));
    }

    public void setBanned(String userId, boolean banned) {
        cacheService.put(RedisKeys.userSecurity(userId) + ":banned", String.valueOf(banned), java.time.Duration.ofDays(7));
    }
}
