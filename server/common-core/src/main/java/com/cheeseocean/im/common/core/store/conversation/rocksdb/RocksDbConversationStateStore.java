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
    public void setUserMaxSeq(String userId, String conversationId, long seq) {
        support.put(RedisKeys.userMaxSeq(userId, conversationId), String.valueOf(seq), null);
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
    public void incrementUnread(String userId, String conversationId) {
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
    public void setUnread(String userId, String conversationId, int unreadCount) {
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
