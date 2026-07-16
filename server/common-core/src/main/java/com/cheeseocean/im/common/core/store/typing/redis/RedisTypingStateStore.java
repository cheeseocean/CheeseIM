package com.cheeseocean.im.common.core.store.typing.redis;

import com.cheeseocean.im.common.api.enums.TypingActionEnum;
import com.cheeseocean.im.common.core.store.typing.TypingStateStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/** Redis 输入中状态实现：START 用 SET NX EX 原子节流，STOP 用 DEL 原子清除。 */
public class RedisTypingStateStore implements TypingStateStore {

    static final DefaultRedisScript<Long> UPDATE_SCRIPT = new DefaultRedisScript<>("""
            if ARGV[1] == 'START' then
                local accepted = redis.call('SET', KEYS[1], '1', 'NX', 'EX', tonumber(ARGV[2]))
                if accepted then return 1 else return 0 end
            end
            return redis.call('DEL', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisTypingStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean update(String senderId, String conversationId, TypingActionEnum action, int ttlSeconds) {
        if (senderId == null || conversationId == null || action == null || ttlSeconds <= 0) {
            return false;
        }
        String key = "typing:state:{" + conversationId + "}:" + senderId;
        Long updated = redisTemplate.execute(UPDATE_SCRIPT, List.of(key), action.name(), Integer.toString(ttlSeconds));
        return Long.valueOf(1L).equals(updated);
    }
}
