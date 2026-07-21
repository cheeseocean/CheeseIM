package com.cheeseocean.im.authcenter.repository;

import com.cheeseocean.im.common.core.store.session.refresh.RefreshTokenStateStore;
import org.springframework.stereotype.Repository;

/**
 * authcenter 对 refresh token family 状态机的仓储适配。
 */
@Repository
public class RefreshTokenRepository {

    private final RefreshTokenStateStore refreshTokenStateStore;

    public RefreshTokenRepository(RefreshTokenStateStore refreshTokenStateStore) {
        this.refreshTokenStateStore = refreshTokenStateStore;
    }

    public RefreshTokenStateStore.IssuedToken createFamily(
            String sessionId,
            long ttlMs,
            long nowMillis) {
        return refreshTokenStateStore.createFamily(sessionId, ttlMs, nowMillis);
    }

    public RefreshTokenStateStore.Inspection inspect(String refreshToken, long nowMillis) {
        return refreshTokenStateStore.inspect(refreshToken, nowMillis);
    }

    public RefreshTokenStateStore.Rotation rotate(String refreshToken, long nowMillis) {
        return refreshTokenStateStore.rotate(refreshToken, nowMillis);
    }

    public void revokeFamily(String familyId) {
        refreshTokenStateStore.revokeFamily(familyId);
    }
}
