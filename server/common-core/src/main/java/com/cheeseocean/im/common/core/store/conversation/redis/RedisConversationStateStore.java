package com.cheeseocean.im.common.core.store.conversation.redis;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RedisConversationStateStore implements ConversationStateStore {

    private static final DefaultRedisScript<List> ADVANCE_READ_STATE_SCRIPT =
            new DefaultRedisScript<>(advanceReadStateLua(), List.class);
    private static final DefaultRedisScript<Long> ADVANCE_USER_MAX_SCRIPT = new DefaultRedisScript<>("""
            local current = tonumber(redis.call('GET', KEYS[1])) or 0
            local requested = tonumber(ARGV[1]) or 0
            if requested <= current then return current end
            local delta = requested - current
            redis.call('SET', KEYS[1], requested)
            if ARGV[2] == '1' then redis.call('INCRBY', KEYS[2], delta) end
            return requested
            """, Long.class);

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
    public void advanceUserMaxSeq(String userId, String conversationId, long maxSeq, boolean countUnread) {
        redisTemplate.execute(ADVANCE_USER_MAX_SCRIPT, List.of(
                RedisKeys.userMaxSeq(userId, conversationId), RedisKeys.userUnread(userId, conversationId)),
                String.valueOf(maxSeq), countUnread ? "1" : "0");
    }

    @Override
    public Long getUserMaxSeq(String userId, String conversationId) {
        return parseLong(redisTemplate.opsForValue().get(RedisKeys.userMaxSeq(userId, conversationId)));
    }

    @Override
    public void setUserReadSeq(String userId, String conversationId, long seq) {
        redisTemplate.opsForValue().set(RedisKeys.userReadSeq(userId, conversationId), String.valueOf(seq));
    }

    @Override
    public Long getUserReadSeq(String userId, String conversationId) {
        return parseLong(redisTemplate.opsForValue().get(RedisKeys.userReadSeq(userId, conversationId)));
    }

    @Override
    public ReadState advanceReadState(String userId, String conversationId, long requestedReadSeq,
                                      long knownReadSeq, long knownMaxSeq) {
        List<String> keys = List.of(
                RedisKeys.userReadSeq(userId, conversationId),
                RedisKeys.userMaxSeq(userId, conversationId),
                RedisKeys.userUnread(userId, conversationId));
        List<?> result = redisTemplate.execute(
                ADVANCE_READ_STATE_SCRIPT,
                keys,
                String.valueOf(requestedReadSeq),
                String.valueOf(Math.max(knownReadSeq, 0L)),
                String.valueOf(Math.max(knownMaxSeq, 0L)));
        if (result == null || result.size() < 3) {
            throw new IllegalStateException("Redis advance read state returned an invalid result");
        }
        long readSeq = Long.parseLong(String.valueOf(result.get(0)));
        long unread = Long.parseLong(String.valueOf(result.get(1)));
        boolean changed = Long.parseLong(String.valueOf(result.get(2))) == 1L;
        return new ReadState(readSeq, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, unread)), changed);
    }

    @Override
    public void incrementUnread(String userId, String conversationId) {
        redisTemplate.opsForValue().increment(RedisKeys.userUnread(userId, conversationId));
    }

    @Override
    public void incrementUnreadBy(String userId, String conversationId, int delta) {
        if (delta <= 0) return;
        redisTemplate.opsForValue().increment(RedisKeys.userUnread(userId, conversationId), delta);
    }

    @Override
    public int getUnread(String userId, String conversationId) {
        Long value = parseLong(redisTemplate.opsForValue().get(RedisKeys.userUnread(userId, conversationId)));
        return value == null ? 0 : value.intValue();
    }

    @Override
    public void setUnread(String userId, String conversationId, int unreadCount) {
        redisTemplate.opsForValue().set(
                RedisKeys.userUnread(userId, conversationId),
                String.valueOf(Math.max(unreadCount, 0))
        );
    }

    @Override
    public void setLastMessageSummary(String conversationId, String summary) {
        redisTemplate.opsForValue().set(RedisKeys.convLastMsg(conversationId), summary);
    }

    @Override
    public String getLastMessageSummary(String conversationId) {
        return redisTemplate.opsForValue().get(RedisKeys.convLastMsg(conversationId));
    }

    @Override
    public Map<String, String> getLastMessageSummaries(List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<String> ids = new ArrayList<>();
        for (String conversationId : conversationIds) {
            if (conversationId != null && !conversationId.isBlank()) {
                ids.add(conversationId);
            }
        }
        if (ids.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<String> keys = ids.stream().map(RedisKeys::convLastMsg).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            String value = values == null ? null : values.get(i);
            if (value != null) {
                result.put(ids.get(i), value);
            }
        }
        return result;
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

    private static String advanceReadStateLua() {
        return """
                local readKey = KEYS[1]
                local maxKey = KEYS[2]
                local unreadKey = KEYS[3]
                local requestedReadSeq = tonumber(ARGV[1]) or 0
                local knownReadSeq = tonumber(ARGV[2]) or 0
                local knownMaxSeq = tonumber(ARGV[3]) or 0

                local currentReadSeq = tonumber(redis.call('GET', readKey)) or knownReadSeq
                if currentReadSeq < knownReadSeq then currentReadSeq = knownReadSeq end
                local currentMaxSeq = tonumber(redis.call('GET', maxKey)) or knownMaxSeq
                if currentMaxSeq < knownMaxSeq then currentMaxSeq = knownMaxSeq end

                local nextReadSeq = requestedReadSeq
                if nextReadSeq > currentMaxSeq then nextReadSeq = currentMaxSeq end
                if nextReadSeq < currentReadSeq then nextReadSeq = currentReadSeq end
                local changed = 0
                if nextReadSeq > currentReadSeq then changed = 1 end
                local unread = currentMaxSeq - nextReadSeq
                if unread < 0 then unread = 0 end

                redis.call('SET', readKey, nextReadSeq)
                redis.call('SET', maxKey, currentMaxSeq)
                redis.call('SET', unreadKey, unread)
                return {nextReadSeq, unread, changed}
                """;
    }
}
