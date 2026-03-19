package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.constants.RedisKeys;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.entity.DeliveryState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class MessageIdempotencyService {

    private static final long TTL_HOURS = 24;

    private final StringRedisTemplate redisTemplate;

    public MessageIdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<DeliveryResult> findExisting(String senderId, String conversationId, String clientMsgId) {
        String raw = redisTemplate.opsForValue().get(RedisKeys.idempotency(senderId, conversationId, clientMsgId));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = raw.split("\\|", 2);
        DeliveryResult result = new DeliveryResult();
        result.setSuccess(true);
        result.setServerMsgId(parts[0]);
        if (parts.length > 1) {
            result.setStatus(parts[1]);
            result.setState(DeliveryState.valueOf(parts[1]));
        }
        return Optional.of(result);
    }

    public void remember(String senderId, String conversationId, String clientMsgId, DeliveryResult result) {
        String value = result.getServerMsgId() + "|" + result.getState().name();
        redisTemplate.opsForValue().set(
                RedisKeys.idempotency(senderId, conversationId, clientMsgId),
                value,
                TTL_HOURS,
                TimeUnit.HOURS
        );
    }
}
