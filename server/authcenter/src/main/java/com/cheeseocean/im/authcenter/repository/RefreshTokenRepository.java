package com.cheeseocean.im.authcenter.repository;

import com.cheeseocean.im.common.core.cache.CacheRegion;
import com.cheeseocean.im.common.core.cache.CacheStore;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class RefreshTokenRepository {

    private static final String REFRESH_TOKEN_PREFIX = "cheese_im:refresh_token:";

    private final CacheRegion<String> refreshTokenCache;

    public RefreshTokenRepository(CacheStore cacheStore) {
        this.refreshTokenCache = cacheStore.region(REFRESH_TOKEN_PREFIX, String.class, Duration.ofHours(24));
    }

    public void save(String refreshToken, String sessionId, long ttlMs) {
        refreshTokenCache.put(refreshToken, sessionId, Duration.ofMillis(ttlMs));
    }

    public String getSessionId(String refreshToken) {
        return refreshTokenCache.get(refreshToken);
    }

    public void delete(String refreshToken) {
        refreshTokenCache.evict(refreshToken);
    }
}
