package com.cheeseocean.im.authcenter.auth;

import com.cheeseocean.im.authcenter.config.AuthCenterConfig;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.enums.PlatformType;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenIssuer {

    private final AuthCenterConfig authCenterConfig;
    private SecretKey secretKey;

    public JwtTokenIssuer(AuthCenterConfig authCenterConfig) {
        this.authCenterConfig = authCenterConfig;
    }

    public TokenPair issue(SessionPrincipal session) {
        long now = System.currentTimeMillis();
        long accessExpireAt = now + authCenterConfig.getSecurity().getTokenExpiration();
        long refreshExpireAt = now + authCenterConfig.getRefreshToken().getTtlMs();

        String accessToken = Jwts.builder()
                .setSubject(session.getUserId())
                .claim("platformID", platformId(session.getPlatform()))
                .claim("sid", session.getSessionId())
                .claim("did", session.getDeviceId())
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(accessExpireAt))
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();

        String refreshToken = UUID.randomUUID().toString();
        TokenPair pair = new TokenPair();
        pair.setAccessToken(accessToken);
        pair.setRefreshToken(refreshToken);
        pair.setAccessExpireAt(accessExpireAt);
        pair.setRefreshExpireAt(refreshExpireAt);
        return pair;
    }

    private SecretKey getSecretKey() {
        if (secretKey == null) {
            secretKey = Keys.hmacShaKeyFor(authCenterConfig.getSecurity().getJwtSecret().getBytes());
        }
        return secretKey;
    }

    private int platformId(String platform) {
        return PlatformType.fromName(platform).getCode();
    }

    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
        private Long accessExpireAt;
        private Long refreshExpireAt;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public Long getAccessExpireAt() {
            return accessExpireAt;
        }

        public void setAccessExpireAt(Long accessExpireAt) {
            this.accessExpireAt = accessExpireAt;
        }

        public Long getRefreshExpireAt() {
            return refreshExpireAt;
        }

        public void setRefreshExpireAt(Long refreshExpireAt) {
            this.refreshExpireAt = refreshExpireAt;
        }
    }
}
