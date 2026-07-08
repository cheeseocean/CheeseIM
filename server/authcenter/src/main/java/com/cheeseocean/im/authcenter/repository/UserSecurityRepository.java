package com.cheeseocean.im.authcenter.repository;

import com.cheeseocean.im.common.api.session.UserSecurityState;
import com.cheeseocean.im.common.core.cache.MultiLevelCacheService;
import com.cheeseocean.im.common.core.business.repository.UserSecurityStateRepository;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class UserSecurityRepository {

    private static final long INITIAL_TOKEN_VERSION = 1L;
    private static final Duration SECURITY_CACHE_TTL = Duration.ofDays(7);

    private final MultiLevelCacheService cacheService;
    private final UserSecurityStateRepository userSecurityStateRepository;

    public UserSecurityRepository(MultiLevelCacheService cacheService,
                                  UserSecurityStateRepository userSecurityStateRepository) {
        this.cacheService = cacheService;
        this.userSecurityStateRepository = userSecurityStateRepository;
    }

    public boolean isBanned(String userId) {
        return loadState(userId).isBanned();
    }

    public long tokenVersion(String userId) {
        return loadState(userId).getTokenVersion();
    }

    public boolean matchesTokenVersion(String userId, Long tokenVersion) {
        if (tokenVersion == null) {
            return false;
        }
        return tokenVersion.equals(tokenVersion(userId));
    }

    public long bumpTokenVersion(String userId) {
        UserSecurityState state = userSecurityStateRepository.bumpTokenVersion(userId);
        cacheService.put(cacheKey(userId), state, SECURITY_CACHE_TTL);
        return state.getTokenVersion();
    }

    public void setBanned(String userId, boolean banned) {
        UserSecurityState state = userSecurityStateRepository.setBanned(userId, banned);
        cacheService.put(cacheKey(userId), state, SECURITY_CACHE_TTL);
    }

    private UserSecurityState loadState(String userId) {
        UserSecurityState state = cacheService.getOrLoad(
                cacheKey(userId),
                UserSecurityState.class,
                SECURITY_CACHE_TTL,
                () -> userSecurityStateRepository.findByUserId(userId)
                        .orElseGet(() -> defaultState(userId))
        );
        return state == null ? defaultState(userId) : state;
    }

    private UserSecurityState defaultState(String userId) {
        UserSecurityState state = new UserSecurityState();
        state.setUserId(userId);
        state.setTokenVersion(INITIAL_TOKEN_VERSION);
        state.setBanned(false);
        state.setUpdatedAt(0L);
        return state;
    }

    private String cacheKey(String userId) {
        return RedisKeys.userSecurity(userId);
    }
}
