package com.cheeseocean.im.authcenter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cheeseim.authcenter")
@Validated
public class AuthCenterConfig {

    @Valid
    private final Security security = new Security();
    @Valid
    private final IdentityAssertion identityAssertion = new IdentityAssertion();
    private final WsTicket wsTicket = new WsTicket();
    private final RefreshToken refreshToken = new RefreshToken();

    public Security getSecurity() {
        return security;
    }

    public WsTicket getWsTicket() {
        return wsTicket;
    }

    public IdentityAssertion getIdentityAssertion() {
        return identityAssertion;
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

    /**
     * 可信业务身份源签发的短期登录断言配置。
     *
     * <p>默认关闭并拒绝登录；只有显式配置独立密钥后才接受 assertion，
     * 不复用 IM access token 密钥，避免两个信任域互相伪造令牌。</p>
     */
    public static class IdentityAssertion {
        private boolean enabled;
        private String issuer = "cheeseim-account";
        private String audience = "cheeseim-im";
        private String hmacSecret;
        private long maxLifetimeMs = 60_000L;
        private long allowedClockSkewMs = 5_000L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public String getHmacSecret() { return hmacSecret; }
        public void setHmacSecret(String hmacSecret) { this.hmacSecret = hmacSecret; }
        public long getMaxLifetimeMs() { return maxLifetimeMs; }
        public void setMaxLifetimeMs(long maxLifetimeMs) { this.maxLifetimeMs = maxLifetimeMs; }
        public long getAllowedClockSkewMs() { return allowedClockSkewMs; }
        public void setAllowedClockSkewMs(long allowedClockSkewMs) { this.allowedClockSkewMs = allowedClockSkewMs; }

        /**
         * 启用可信断言时在启动阶段校验完整安全配置，避免请求到来后才发现密钥不可用。
         */
        @AssertTrue(message = "enabled identity assertion requires issuer/audience, a 32-byte secret and positive lifetime")
        public boolean isSafeConfiguration() {
            return !enabled || issuer != null && !issuer.isBlank()
                    && audience != null && !audience.isBlank()
                    && hmacSecret != null && hmacSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= 32
                    && maxLifetimeMs > 0L
                    && allowedClockSkewMs >= 0L;
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
