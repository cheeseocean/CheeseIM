package com.cheeseocean.im.postoffice.auth;

import com.cheeseocean.im.common.constants.MessageConstants;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * JWT认证服务实现
 * 参照OpenIM Server的Token验证机制
 * 
 * @author CheeseIM
 */
@Service
public class JwtAuthService implements AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthService.class);
    
    @Value("${cheese.im.security.jwt-secret:CheeseIM2024Secret!}")
    private String jwtSecret;
    
    @Value("${cheese.im.security.token-expiration:86400000}")
    private long tokenExpiration;
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    private SecretKey secretKey;
    
    public JwtAuthService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 初始化密钥
     */
    private SecretKey getSecretKey() {
        if (secretKey == null) {
            secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
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
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSecretKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            String userID = claims.getSubject();
            Integer platformID = claims.get("platformID", Integer.class);
            Date expiration = claims.getExpiration();
            
            if (userID == null || platformID == null) {
                return AuthResult.failure("Token格式无效");
            }
            
            // 检查Token是否过期
            if (expiration.before(new Date())) {
                return AuthResult.failure("Token已过期");
            }
            
            // 检查Redis中的Token状态
            String tokenKey = MessageConstants.REDIS_KEY_USER_TOKEN + userID + ":" + platformID;
            String storedToken = (String) redisTemplate.opsForValue().get(tokenKey);
            
            if (storedToken == null) {
                return AuthResult.failure("Token不存在或已失效");
            }
            
            if (!token.equals(storedToken)) {
                return AuthResult.failure("Token无效");
            }
            
            // 更新Token的最后访问时间
            redisTemplate.expire(tokenKey, tokenExpiration, TimeUnit.MILLISECONDS);
            
            logger.debug("Token validation success: userID={}, platformID={}", userID, platformID);
            return AuthResult.success(userID, platformID, expiration.getTime());
            
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
            Date now = new Date();
            Date expiration = new Date(now.getTime() + tokenExpiration);
            
            String token = Jwts.builder()
                    .setSubject(userID)
                    .claim("platformID", platformID)
                    .setIssuedAt(now)
                    .setExpiration(expiration)
                    .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                    .compact();
            
            // 将Token存储到Redis
            String tokenKey = MessageConstants.REDIS_KEY_USER_TOKEN + userID + ":" + platformID;
            redisTemplate.opsForValue().set(tokenKey, token, tokenExpiration, TimeUnit.MILLISECONDS);
            
            logger.info("Token generated: userID={}, platformID={}, expiration={}", 
                       userID, platformID, expiration);
            
            return token;
            
        } catch (Exception e) {
            logger.error("Failed to generate token: userID={}, platformID={}", userID, platformID, e);
            throw new RuntimeException("生成Token失败", e);
        }
    }
    
    @Override
    public String refreshToken(String token) {
        try {
            AuthResult authResult = validateToken(token);
            if (!authResult.isSuccess()) {
                throw new RuntimeException("无法刷新无效的Token: " + authResult.getErrorMessage());
            }
            
            // 生成新Token
            String newToken = generateToken(authResult.getUserID(), authResult.getPlatformID());
            
            // 删除旧Token
            String oldTokenKey = MessageConstants.REDIS_KEY_USER_TOKEN + 
                               authResult.getUserID() + ":" + authResult.getPlatformID();
            redisTemplate.delete(oldTokenKey);
            
            logger.info("Token refreshed: userID={}, platformID={}", 
                       authResult.getUserID(), authResult.getPlatformID());
            
            return newToken;
            
        } catch (Exception e) {
            logger.error("Failed to refresh token", e);
            throw new RuntimeException("刷新Token失败", e);
        }
    }
    
    /**
     * 撤销Token
     */
    public boolean revokeToken(String userID, Integer platformID) {
        try {
            String tokenKey = MessageConstants.REDIS_KEY_USER_TOKEN + userID + ":" + platformID;
            Boolean deleted = redisTemplate.delete(tokenKey);
            
            logger.info("Token revoked: userID={}, platformID={}, success={}", 
                       userID, platformID, deleted);
            
            return Boolean.TRUE.equals(deleted);
            
        } catch (Exception e) {
            logger.error("Failed to revoke token: userID={}, platformID={}", userID, platformID, e);
            return false;
        }
    }
    
    /**
     * 撤销用户的所有Token
     */
    public int revokeAllUserTokens(String userID) {
        try {
            String pattern = MessageConstants.REDIS_KEY_USER_TOKEN + userID + ":*";
            var keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                Long deleted = redisTemplate.delete(keys);
                logger.info("All tokens revoked for user: userID={}, count={}", userID, deleted);
                return deleted != null ? deleted.intValue() : 0;
            }
            
            return 0;
            
        } catch (Exception e) {
            logger.error("Failed to revoke all tokens for user: {}", userID, e);
            return 0;
        }
    }
    
    /**
     * 检查Token是否存在
     */
    public boolean isTokenExists(String userID, Integer platformID) {
        try {
            String tokenKey = MessageConstants.REDIS_KEY_USER_TOKEN + userID + ":" + platformID;
            return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey));
            
        } catch (Exception e) {
            logger.error("Failed to check token existence: userID={}, platformID={}", 
                        userID, platformID, e);
            return false;
        }
    }
    
    /**
     * 获取Token的剩余有效时间（秒）
     */
    public long getTokenTTL(String userID, Integer platformID) {
        try {
            String tokenKey = MessageConstants.REDIS_KEY_USER_TOKEN + userID + ":" + platformID;
            Long ttl = redisTemplate.getExpire(tokenKey, TimeUnit.SECONDS);
            return ttl != null ? ttl : -1;
            
        } catch (Exception e) {
            logger.error("Failed to get token TTL: userID={}, platformID={}", 
                        userID, platformID, e);
            return -1;
        }
    }
}
