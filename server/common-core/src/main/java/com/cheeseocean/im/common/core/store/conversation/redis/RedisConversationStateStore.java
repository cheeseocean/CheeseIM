package com.cheeseocean.im.common.core.store.conversation.redis;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;

public class RedisConversationStateStore implements ConversationStateStore {

    private final StringRedisTemplate redisTemplate;

    public RedisConversationStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    @Override
    public void setConversationMinSeqIfAbsent(String conversationId, long seq) {
        redisTemplate.opsForValue().setIfAbsent(RedisKeys.convMinSeq(conversationId), String.valueOf(seq));
    }

    @Override
    public void setConversationMaxSeq(String conversationId, long seq) {
        redisTemplate.opsForValue().set(RedisKeys.convMaxSeq(conversationId), String.valueOf(seq));
    }

    @Override
    public Long getConversationMaxSeq(String conversationId) {
        return parseLong(redisTemplate.opsForValue().get(RedisKeys.convMaxSeq(conversationId)));
    }

    @Override
    public void setUserMaxSeq(String userId, String conversationId, long seq) {
        redisTemplate.opsForValue().set(RedisKeys.userMaxSeq(userId, conversationId), String.valueOf(seq));
    }

    @Override
    public void setUserReadSeq(String userId, String conversationId, long seq) {
        redisTemplate.opsForValue().set(RedisKeys.userReadSeq(userId, conversationId), String.valueOf(seq));
    }

    @Override
    public void incrementUnread(String userId, String conversationId) {
        redisTemplate.opsForValue().increment(RedisKeys.userUnread(userId, conversationId));
    }

    @Override
    public int getUnread(String userId, String conversationId) {
        Long value = parseLong(redisTemplate.opsForValue().get(RedisKeys.userUnread(userId, conversationId)));
        return value == null ? 0 : value.intValue();
    }

    @Override
    public void setLastMessageSummary(String conversationId, String summary) {
        redisTemplate.opsForValue().set(RedisKeys.convLastMsg(conversationId), summary);
    }

    @Override
    public String getLastMessageSummary(String conversationId) {
        return redisTemplate.opsForValue().get(RedisKeys.convLastMsg(conversationId));
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
