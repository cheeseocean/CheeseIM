package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.constants.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ConsumerDedupService {

    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public ConsumerDedupService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean markIfAbsent(String consumerGroup, String eventId) {
        Boolean marked = redisTemplate.opsForValue().setIfAbsent(
                RedisKeys.consumerDedup(consumerGroup, eventId),
                "1",
                TTL
        );
        return Boolean.TRUE.equals(marked);
    }
}
