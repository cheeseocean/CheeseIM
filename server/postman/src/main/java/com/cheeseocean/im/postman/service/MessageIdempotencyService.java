package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.dto.message.DeliveryResult;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.enums.DeliveryState;
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
        String raw = redisTemplate.opsForValue().get(RedisKeys.postmanIdem(conversationId, clientMsgId));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] parts = raw.split("\\|", -1);
        DeliveryResult result = new DeliveryResult();
        result.setSuccess(true);
        result.setServerMsgId(parts[0]);
        if (parts.length == 2) {
            result.setStatus(parts[1]);
            result.setState(DeliveryState.valueOf(parts[1]));
            return Optional.of(result);
        }
        if (parts.length > 1 && !parts[1].isBlank()) {
            result.setStatus(parts[1]);
        }
        if (parts.length > 2 && !parts[2].isBlank()) {
            result.setConversationSeq(Long.parseLong(parts[2]));
        }
        if (parts.length > 3 && !parts[3].isBlank()) {
            result.setState(DeliveryState.valueOf(parts[3]));
        } else if (result.getStatus() != null && !result.getStatus().isBlank()) {
            result.setState(DeliveryState.valueOf(result.getStatus()));
        }
        return Optional.of(result);
    }

    public void remember(String senderId, String conversationId, String clientMsgId, DeliveryResult result) {
        String status = result.getStatus() == null ? "" : result.getStatus();
        String conversationSeq = result.getConversationSeq() == null ? "" : String.valueOf(result.getConversationSeq());
        String state = result.getState() == null ? "" : result.getState().name();
        String value = String.join("|", result.getServerMsgId(), status, conversationSeq, state);
        redisTemplate.opsForValue().set(
                RedisKeys.postmanIdem(conversationId, clientMsgId),
                value,
                TTL_HOURS,
                TimeUnit.HOURS
        );
    }
}
