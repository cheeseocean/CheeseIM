package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConversationSeqService {

    private final StringRedisTemplate redisTemplate;

    public ConversationSeqService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long nextSeq(String conversationId) {
        Long seq = redisTemplate.opsForValue().increment(RedisKeys.convMaxSeq(conversationId));
        if (seq == null) {
            throw new IllegalStateException("Failed to allocate conversation seq");
        }
        return seq;
    }
}
