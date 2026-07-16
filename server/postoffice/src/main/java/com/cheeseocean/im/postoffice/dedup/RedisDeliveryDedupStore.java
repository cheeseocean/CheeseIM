package com.cheeseocean.im.postoffice.dedup;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Redis 原子 claim/commit/abort 投递去重状态机。 */
@Service
public class RedisDeliveryDedupStore implements DeliveryDedupStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisDeliveryDedupStore.class);
    private static final String CLAIM_PREFIX = "claim:";

    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then
              redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2], 'NX')
              return 1
            end
            if current == 'delivered' then return 2 end
            return 3
            """, Long.class);
    private static final DefaultRedisScript<Long> COMMIT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              redis.call('SET', KEYS[1], 'delivered', 'EX', ARGV[2])
              return 1
            end
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> ABORT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long deliveredTtlSeconds;
    private final long claimTtlSeconds;

    public RedisDeliveryDedupStore(StringRedisTemplate redisTemplate,
                                   @Value("${cheeseim.delivery.dedup.ttl-seconds:600}") long deliveredTtlSeconds,
                                   @Value("${cheeseim.delivery.dedup.claim-ttl-seconds:30}") long claimTtlSeconds) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.deliveredTtlSeconds = Math.max(1L, deliveredTtlSeconds);
        this.claimTtlSeconds = Math.max(1L, claimTtlSeconds);
    }

    @Override
    public Claim claim(String deliveryId, String userId, String deviceId) {
        if (deliveryId == null || userId == null) {
            ImMetrics.dedup("unavailable");
            return Claim.status(ClaimStatus.UNAVAILABLE);
        }
        String key = RedisKeys.deliveryIdem(deliveryId, userId, deviceId == null ? "*" : deviceId);
        String token = UUID.randomUUID().toString();
        try {
            Long result = redisTemplate.execute(CLAIM_SCRIPT, List.of(key),
                    CLAIM_PREFIX + token, Long.toString(claimTtlSeconds));
            if (result == null) {
                logger.warn("Redis delivery claim returned null for key={}", key);
                return Claim.status(ClaimStatus.UNAVAILABLE);
            }
            Claim claim = switch (result.intValue()) {
                case 1 -> Claim.acquired(key, token);
                case 2 -> Claim.status(ClaimStatus.DELIVERED);
                case 3 -> Claim.status(ClaimStatus.IN_PROGRESS);
                default -> Claim.status(ClaimStatus.UNAVAILABLE);
            };
            ImMetrics.dedup(claim.status().name().toLowerCase());
            return claim;
        } catch (RuntimeException e) {
            logger.error("Redis delivery claim failed for key={}", key, e);
            ImMetrics.dedup("unavailable");
            return Claim.status(ClaimStatus.UNAVAILABLE);
        }
    }

    @Override
    public boolean commit(Claim claim) {
        return transition(COMMIT_SCRIPT, claim, Long.toString(deliveredTtlSeconds), "commit");
    }

    @Override
    public boolean abort(Claim claim) {
        return transition(ABORT_SCRIPT, claim, null, "abort");
    }

    private boolean transition(DefaultRedisScript<Long> script, Claim claim, String ttl, String operation) {
        if (claim == null || claim.status() != ClaimStatus.ACQUIRED || claim.key() == null || claim.token() == null) {
            return false;
        }
        try {
            String claimValue = CLAIM_PREFIX + claim.token();
            Long result = ttl == null
                    ? redisTemplate.execute(script, List.of(claim.key()), claimValue)
                    : redisTemplate.execute(script, List.of(claim.key()), claimValue, ttl);
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException e) {
            logger.error("Redis delivery {} failed for key={}", operation, claim.key(), e);
            return false;
        }
    }
}
