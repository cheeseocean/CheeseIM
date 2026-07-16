package com.cheeseocean.im.common.core.store.conversation.rocksdb;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.store.rocksdb.RocksDbSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Objects;

public class RocksDbConversationStateStore implements ConversationStateStore {

    private final RocksDbSupport support;

    public RocksDbConversationStateStore(Path dataDirectory, ObjectMapper objectMapper) {
        this.support = new RocksDbSupport(
                Objects.requireNonNull(dataDirectory, "dataDirectory"),
                Objects.requireNonNull(objectMapper, "objectMapper")
        );
    }

    @Override
    public void setConversationMinSeqIfAbsent(String conversationId, long seq) {
        String key = RedisKeys.convMinSeq(conversationId);
        if (support.get(key, String.class) == null) {
            support.put(key, String.valueOf(seq), null);
        }
    }

    @Override
    public void setConversationMaxSeq(String conversationId, long seq) {
        support.put(RedisKeys.convMaxSeq(conversationId), String.valueOf(seq), null);
    }

    @Override
    public Long getConversationMaxSeq(String conversationId) {
        return parseLong(support.get(RedisKeys.convMaxSeq(conversationId), String.class));
    }

    @Override
    public synchronized void setUserMaxSeq(String userId, String conversationId, long seq) {
        String key = RedisKeys.userMaxSeq(userId, conversationId);
        Long current = parseLong(support.get(key, String.class));
        support.put(key, String.valueOf(current == null ? seq : Math.max(current, seq)), null);
    }

    @Override
    public synchronized void advanceUserMaxSeq(String userId, String conversationId, long maxSeq, boolean countUnread) {
        String maxKey = RedisKeys.userMaxSeq(userId, conversationId);
        Long stored = parseLong(support.get(maxKey, String.class));
        long current = stored == null ? 0L : stored;
        if (maxSeq <= current) return;
        support.put(maxKey, String.valueOf(maxSeq), null);
        if (countUnread) {
            String unreadKey = RedisKeys.userUnread(userId, conversationId);
            Long unread = parseLong(support.get(unreadKey, String.class));
            support.put(unreadKey, String.valueOf((unread == null ? 0L : unread) + maxSeq - current), null);
        }
    }

    @Override
    public Long getUserMaxSeq(String userId, String conversationId) {
        return parseLong(support.get(RedisKeys.userMaxSeq(userId, conversationId), String.class));
    }

    @Override
    public void setUserReadSeq(String userId, String conversationId, long seq) {
        support.put(RedisKeys.userReadSeq(userId, conversationId), String.valueOf(seq), null);
    }

    @Override
    public Long getUserReadSeq(String userId, String conversationId) {
        return parseLong(support.get(RedisKeys.userReadSeq(userId, conversationId), String.class));
    }

    @Override
    public synchronized ReadState advanceReadState(String userId, String conversationId, long requestedReadSeq,
                                                   long knownReadSeq, long knownMaxSeq) {
        String readKey = RedisKeys.userReadSeq(userId, conversationId);
        String maxKey = RedisKeys.userMaxSeq(userId, conversationId);
        String unreadKey = RedisKeys.userUnread(userId, conversationId);
        Long storedReadSeq = parseLong(support.get(readKey, String.class));
        Long storedMaxSeq = parseLong(support.get(maxKey, String.class));
        long currentReadSeq = Math.max(storedReadSeq == null ? 0L : storedReadSeq, Math.max(knownReadSeq, 0L));
        long currentMaxSeq = Math.max(storedMaxSeq == null ? 0L : storedMaxSeq, Math.max(knownMaxSeq, 0L));
        long nextReadSeq = Math.max(currentReadSeq, Math.min(requestedReadSeq, currentMaxSeq));
        int unread = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, currentMaxSeq - nextReadSeq));
        support.put(readKey, String.valueOf(nextReadSeq), null);
        support.put(unreadKey, String.valueOf(unread), null);
        return new ReadState(nextReadSeq, unread, nextReadSeq > currentReadSeq);
    }

    @Override
    public synchronized void incrementUnread(String userId, String conversationId) {
        String key = RedisKeys.userUnread(userId, conversationId);
        long next = parseLong(support.get(key, String.class)) == null ? 1L : parseLong(support.get(key, String.class)) + 1L;
        support.put(key, String.valueOf(next), null);
    }

    @Override
    public int getUnread(String userId, String conversationId) {
        Long value = parseLong(support.get(RedisKeys.userUnread(userId, conversationId), String.class));
        return value == null ? 0 : value.intValue();
    }

    @Override
    public synchronized void setUnread(String userId, String conversationId, int unreadCount) {
        support.put(RedisKeys.userUnread(userId, conversationId), String.valueOf(Math.max(unreadCount, 0)), null);
    }

    @Override
    public void setLastMessageSummary(String conversationId, String summary) {
        support.put(RedisKeys.convLastMsg(conversationId), summary, null);
    }

    @Override
    public String getLastMessageSummary(String conversationId) {
        return support.get(RedisKeys.convLastMsg(conversationId), String.class);
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
