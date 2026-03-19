package com.cheeseocean.im.authcenter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cheeseim.authcenter")
public class AuthCenterConfig {

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
