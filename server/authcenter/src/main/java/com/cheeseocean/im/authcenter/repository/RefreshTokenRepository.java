package com.cheeseocean.im.authcenter.repository;

import com.cheeseocean.im.common.core.cache.MultiLevelCacheService;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class RefreshTokenRepository {

    private static final String REFRESH_TOKEN_PREFIX = "cheese_im:refresh_token:";

    private final MultiLevelCacheService cacheService;

    public RefreshTokenRepository(MultiLevelCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public void save(String refreshToken, String sessionId, long ttlMs) {
        cacheService.put(REFRESH_TOKEN_PREFIX + refreshToken, sessionId, Duration.ofMillis(ttlMs));
    }

    public String getSessionId(String refreshToken) {
        return cacheService.getOrLoad(REFRESH_TOKEN_PREFIX + refreshToken, String.class, Duration.ofHours(24), () -> null);
    }

    public void delete(String refreshToken) {
        cacheService.evict(REFRESH_TOKEN_PREFIX + refreshToken);
    }
}
