package com.cheeseocean.im.authcenter.auth;

import com.cheeseocean.im.authcenter.config.AuthCenterConfig;
import com.cheeseocean.im.common.core.enums.PlatformType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class AccessTokenService {

    private final AuthCenterConfig authCenterConfig;
    private SecretKey secretKey;

    public AccessTokenService(AuthCenterConfig authCenterConfig) {
        this.authCenterConfig = authCenterConfig;
    }

    public AccessTokenPrincipal validate(String accessToken) {
        try {
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("access token missing");
            }
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
            String userId = claims.getSubject();
            Integer platformId = claims.get("platformID", Integer.class);
            Date expiration = claims.getExpiration();
            if (userId == null || platformId == null) {
                throw new IllegalStateException("access token invalid");
            }
            if (expiration != null && expiration.before(new Date())) {
                throw new IllegalStateException("access token expired");
            }

            AccessTokenPrincipal principal = new AccessTokenPrincipal();
            principal.setAccessToken(accessToken);
            principal.setUserId(userId);
            principal.setPlatformId(platformId);
            principal.setExpireAt(expiration == null ? null : expiration.getTime());
            PlatformType platformType = PlatformType.fromCode(platformId);
            principal.setDeviceId(platformType.getWireName() + "-" + platformId);
            principal.setPlatform(platformType.getWireName());
            return principal;
        } catch (ExpiredJwtException e) {
            throw new IllegalStateException("access token expired");
        } catch (UnsupportedJwtException | MalformedJwtException | SecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("access token invalid");
        }
    }

    public long getTokenExpirationMs() {
        return authCenterConfig.getSecurity().getTokenExpiration();
    }

    private SecretKey getSecretKey() {
        if (secretKey == null) {
            secretKey = Keys.hmacShaKeyFor(authCenterConfig.getSecurity().getJwtSecret().getBytes());
        }
        return secretKey;
    }

}
