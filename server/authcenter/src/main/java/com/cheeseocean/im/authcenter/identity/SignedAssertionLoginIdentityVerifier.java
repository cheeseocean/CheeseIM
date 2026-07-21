package com.cheeseocean.im.authcenter.identity;

import com.cheeseocean.im.authcenter.config.AuthCenterConfig;
import com.cheeseocean.im.common.api.auth.AuthenticationCommand;
import com.cheeseocean.im.common.api.enums.ErrorCode;
import com.cheeseocean.im.common.api.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;

/**
 * 验证可信业务身份源签发的 HS256 短期登录断言。
 *
 * <p>断言必须包含 sub/iss/aud/iat/exp/jti。jti 在 Redis 中一次性消费，
 * 因而 API 层传入的 userId 仅能用于一致性校验，不能成为身份来源。</p>
 */
@Component
public class SignedAssertionLoginIdentityVerifier implements LoginIdentityVerifier {

    private final AuthCenterConfig authCenterConfig;
    private final LoginAssertionReplayGuard replayGuard;

    public SignedAssertionLoginIdentityVerifier(AuthCenterConfig authCenterConfig,
                                                LoginAssertionReplayGuard replayGuard) {
        this.authCenterConfig = authCenterConfig;
        this.replayGuard = replayGuard;
    }

    @Override
    public VerifiedLoginIdentity verify(AuthenticationCommand command) {
        AuthCenterConfig.IdentityAssertion config = authCenterConfig.getIdentityAssertion();
        if (!config.isEnabled()) {
            throw invalid();
        }
        String assertion = command.getIdentityAssertion();
        if (assertion == null || assertion.isBlank()) {
            throw invalid();
        }
        String secret = config.getHmacSecret();

        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Jws<Claims> signedClaims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(assertion);
            if (!"HS256".equals(signedClaims.getHeader().getAlgorithm())) {
                throw invalid();
            }
            Claims claims = signedClaims.getPayload();
            validateClaims(claims, config, command.getUserId());
            long now = System.currentTimeMillis();
            if (!replayGuard.consume(claims.getId(), claims.getExpiration().getTime() - now)) {
                throw invalid();
            }
            return new VerifiedLoginIdentity(claims.getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private void validateClaims(Claims claims,
                                AuthCenterConfig.IdentityAssertion config,
                                String requestedUserId) {
        String userId = claims.getSubject();
        Date issuedAt = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        String jti = claims.getId();
        long now = System.currentTimeMillis();
        long skew = Math.max(0L, config.getAllowedClockSkewMs());

        if (userId == null || userId.isBlank()
                || jti == null || jti.isBlank()
                || issuedAt == null || expiration == null
                || !config.getIssuer().equals(claims.getIssuer())
                || !matchesAudience(claims.get("aud"), config.getAudience())
                || issuedAt.getTime() > now + skew
                || expiration.getTime() <= now
                || expiration.getTime() - issuedAt.getTime() > config.getMaxLifetimeMs()
                || expiration.before(issuedAt)
                || requestedUserId != null && !requestedUserId.isBlank() && !requestedUserId.equals(userId)) {
            throw invalid();
        }
    }

    private boolean matchesAudience(Object claim, String expectedAudience) {
        if (claim instanceof String audience) {
            return expectedAudience.equals(audience);
        }
        if (claim instanceof Collection<?> audiences) {
            return audiences.stream().anyMatch(expectedAudience::equals);
        }
        return false;
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
    }
}
