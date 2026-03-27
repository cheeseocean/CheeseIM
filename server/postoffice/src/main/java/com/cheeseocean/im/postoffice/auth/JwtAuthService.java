package com.cheeseocean.im.postoffice.auth;

import com.cheeseocean.im.common.core.cache.MultiLevelCacheService;
import com.cheeseocean.im.common.core.constants.MessageConstants;
import com.cheeseocean.im.common.core.enums.PlatformType;
import com.cheeseocean.im.postoffice.config.IMServerConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;

/**
 * JWT认证服务实现
 *
 * @author xxxcrel
 */
@Service
public class JwtAuthService implements AuthService {
    
    private static final Logger logger = CommonLoggers.POSTOFFICE;

    @Autowired
    private IMServerConfig imServerConfig;

    private final MultiLevelCacheService cacheService;
    
    private SecretKey secretKey;
    
    public JwtAuthService(MultiLevelCacheService cacheService) {
        this.cacheService = cacheService;
    }
    
    /**
     * 初始化密钥
     */
    private SecretKey getSecretKey() {
        if (secretKey == null) {
            secretKey = Keys.hmacShaKeyFor(imServerConfig.getSecurity().getJwtSecret().getBytes());
        }
        return secretKey;
    }
    
    @Override
    public AuthResult validateToken(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return AuthResult.failure("Token不能为空");
            }
            
            // 解析JWT Token
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            String userID = claims.getSubject();
            Integer platformID = claims.get("platformID", Integer.class);
            Date expiration = claims.getExpiration();
            PlatformType platformType = PlatformType.fromCode(platformID);
            
            if (userID == null || platformType == PlatformType.UNKNOWN) {
                return AuthResult.failure("Token格式无效");
            }
            
            // 检查Token是否过期
            if (expiration.before(new Date())) {
                return AuthResult.failure("Token已过期");
            }
            
            // 检查Redis中的Token状态
            String tokenKey = MessageConstants.REDIS_KEY_USER_TOKEN + userID + ":" + platformType.getCode();
            String storedToken = cacheService.getOrLoad(tokenKey, String.class, Duration.ofMillis(imServerConfig.getSecurity().getTokenExpiration()), () -> null);
            
            if (storedToken == null) {
                return AuthResult.failure("Token不存在或已失效");
            }
            
            if (!token.equals(storedToken)) {
                return AuthResult.failure("Token无效");
            }
            
            // 更新Token的有效期
            cacheService.put(tokenKey, storedToken, Duration.ofMillis(imServerConfig.getSecurity().getTokenExpiration()));
            
            logger.debug("Token validation success: userID={}, platform={}, platformID={}",
                    userID, platformType.getWireName(), platformType.getCode());
            return AuthResult.success(userID, platformType.getCode(), expiration.getTime());
            
        } catch (ExpiredJwtException e) {
            logger.warn("Token expired: {}", e.getMessage());
            return AuthResult.failure("Token已过期");
            
        } catch (UnsupportedJwtException e) {
            logger.warn("Unsupported JWT token: {}", e.getMessage());
            return AuthResult.failure("不支持的Token格式");
            
        } catch (MalformedJwtException e) {
            logger.warn("Malformed JWT token: {}", e.getMessage());
            return AuthResult.failure("Token格式错误");
            
        } catch (SecurityException e) {
            logger.warn("Invalid JWT signature: {}", e.getMessage());
            return AuthResult.failure("Token签名无效");
            
        } catch (IllegalArgumentException e) {
            logger.warn("JWT token compact of handler are invalid: {}", e.getMessage());
            return AuthResult.failure("Token参数无效");
            
        } catch (Exception e) {
            logger.error("Token validation failed", e);
            return AuthResult.failure("Token验证失败");
        }
    }
    
    @Override
    public String generateToken(String userID, Integer platformID) {
        try {
            PlatformType platformType = PlatformType.fromCode(platformID);
            if (platformType == PlatformType.UNKNOWN) {
                throw new IllegalArgumentException("不支持的平台");
            }
            Date now = new Date();
            Date expiration = new Date(now.getTime() + imServerConfig.getSecurity().getTokenExpiration());
            
            String token = Jwts.builder()
                    .setSubject(userID)
                    .claim("platformID", platformType.getCode())
                    .setIssuedAt(now)
                    .setExpiration(expiration)
                    .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                    .compact();
            
            // 将Token存储到Redis
            String tokenKey = MessageConstants.REDIS_KEY_USER_TOKEN + userID + ":" + platformType.getCode();
            cacheService.put(tokenKey, token, Duration.ofMillis(imServerConfig.getSecurity().getTokenExpiration()));
            
            logger.info("Token generated: userID={}, platform={}, platformID={}, expiration={}",
                    userID, platformType.getWireName(), platformType.getCode(), expiration);
            
            return token;
            
        } catch (Exception e) {
            logger.error("Failed to generate token: userID={}, platformID={}", userID, platformID, e);
            throw new RuntimeException("生成Token失败", e);
        }
    }

    public long getTokenExpirationMs() {
        return imServerConfig.getSecurity().getTokenExpiration();
    }
    
}
