package com.cheeseocean.im.authcenter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cheeseim.authcenter")
@Validated
public class AuthCenterConfig {

    @Valid
    private final Security security = new Security();
    private final WsTicket wsTicket = new WsTicket();
    private final RefreshToken refreshToken = new RefreshToken();

    public Security getSecurity() {
        return security;
    }

    public WsTicket getWsTicket() {
        return wsTicket;
    }

    public RefreshToken getRefreshToken() {
        return refreshToken;
    }

    public static class Security {
        /** JWT 签名密钥只允许由 CHEESEIM_AUTH_JWT_SECRET 注入，启动时必须显式提供。 */
        @NotBlank(message = "CHEESEIM_AUTH_JWT_SECRET must be configured")
        @Size(min = 32, message = "CHEESEIM_AUTH_JWT_SECRET must contain at least 32 characters")
        private String jwtSecret;
        private long tokenExpiration = 86_400_000L;

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getTokenExpiration() {
            return tokenExpiration;
        }

        public void setTokenExpiration(long tokenExpiration) {
            this.tokenExpiration = tokenExpiration;
        }
    }

    public static class WsTicket {
        private long ttlMs = 60_000L;

        public long getTtlMs() {
            return ttlMs;
        }

        public void setTtlMs(long ttlMs) {
            this.ttlMs = ttlMs;
        }
    }

    public static class RefreshToken {
        private long ttlMs = 14L * 24 * 60 * 60 * 1000;

        public long getTtlMs() {
            return ttlMs;
        }

        public void setTtlMs(long ttlMs) {
            this.ttlMs = ttlMs;
        }
    }
}
