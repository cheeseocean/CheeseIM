package com.cheeseocean.im.common.core.store.session.refresh;

/**
 * Refresh token family 原子状态存储。
 *
 * <p>一个登录 session 对应一个 family；每次 rotate 只允许当前 token 成功一次。
 * 已消费 token 再次出现时标记整个 family 为 COMPROMISED。</p>
 */
public interface RefreshTokenStateStore {

    /**
     * 创建新的 token family，family 生命周期为绝对 TTL，不随 rotate 延长。
     */
    IssuedToken createFamily(String sessionId, long ttlMs, long nowMillis);

    /**
     * 只读检查 token，供 service 在消费前校验 session/ban/tokenVersion。
     */
    Inspection inspect(String refreshToken, long nowMillis);

    /**
     * 原子消费当前 token 并签发下一代；复用旧 token 会污染整个 family。
     */
    Rotation rotate(String refreshToken, long nowMillis);

    /**
     * logout/kickoff 时撤销指定 family。
     */
    void revokeFamily(String familyId);

    /**
     * 状态只在 authcenter/common-core 内流转，不进入客户端 wire。
     */
    enum TokenStatus {
        CURRENT,
        REUSED,
        REVOKED,
        INVALID
    }

    enum RotationStatus {
        ROTATED,
        REUSED,
        REVOKED,
        INVALID
    }

    record IssuedToken(String familyId,
                       String refreshToken,
                       String sessionId,
                       long expiresAt) {
    }

    record Inspection(TokenStatus status,
                      String familyId,
                      String sessionId,
                      long expiresAt) {
    }

    record Rotation(RotationStatus status,
                    String familyId,
                    String sessionId,
                    String refreshToken,
                    long expiresAt,
                    long generation) {
    }
}
