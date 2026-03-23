package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationReceiptService {

    private final StringRedisTemplate redisTemplate;

    public ConversationReceiptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void applyReadCursor(String userId, String conversationId, Long seq) {
        if (userId == null || userId.isBlank() || conversationId == null || conversationId.isBlank() || seq == null) {
            throw new IllegalArgumentException("read cursor requires userId, conversationId, and seq");
        }
        redisTemplate.opsForValue().set(
                RedisKeys.userReadSeq(userId, conversationId),
                String.valueOf(seq));
    }
}
