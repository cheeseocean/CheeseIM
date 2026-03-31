package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.domain.UserConversationState;
import com.cheeseocean.im.common.core.business.mongo.document.UserConversationStateDoc;
import com.cheeseocean.im.common.core.enums.SessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserConversationStateRepositoryImplTest {

    private MongoTemplate mongoTemplate;
    private RedisTemplate<String, Object> redisTemplate;
    private StringRedisTemplate stringRedisTemplate;
    private UserConversationStateRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        redisTemplate = mock(RedisTemplate.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);

        @SuppressWarnings("unchecked")
        SetOperations<String, Object> setOperations = mock(SetOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> stringValueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);

        repository = new UserConversationStateRepositoryImpl(
                mongoTemplate,
                redisTemplate,
                stringRedisTemplate
        );
    }

    @Test
    void createIfAbsentShouldUpsertWithSetOnInsertOnly() {
        UserConversationState state = conversation("alice", "c1:alice:bob", SessionType.SINGLE.getCode(), "bob");

        repository.createIfAbsent(state);

        verify(mongoTemplate).upsert(
                any(Query.class),
                any(Update.class),
                eq(UserConversationStateDoc.class));
    }

    @Test
    void updateLatestMessageShouldSetSeqAndJson() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        repository.updateLatestMessage("alice", "c1:alice:bob", 42L, "{\"seq\":42}");

        verify(mongoTemplate).upsert(
                any(Query.class),
                updateCaptor.capture(),
                eq(UserConversationStateDoc.class));

        String updateJson = updateCaptor.getValue().getUpdateObject().toString();
        assertNotNull(updateJson);
        assertTrue(updateJson.contains("latestMsgSeq"));
        assertTrue(updateJson.contains("latestMsg"));
    }

    @Test
    void incrementUnreadShouldIncDelta() {
        repository.incrementUnread("alice", "c1:alice:bob", 3);

        verify(mongoTemplate).upsert(
                any(Query.class),
                any(Update.class),
                eq(UserConversationStateDoc.class));
    }

    @Test
    void clearUnreadShouldUseUpdateFirst() {
        repository.clearUnread("alice", "c1:alice:bob");

        verify(mongoTemplate).updateFirst(
                any(Query.class),
                any(Update.class),
                eq(UserConversationStateDoc.class));
    }

    private static UserConversationState conversation(String owner, String convId, int type, String target) {
        UserConversationState state = new UserConversationState();
        state.setOwnerUserId(owner);
        state.setConversationId(convId);
        state.setConversationType(type);
        state.setTargetId(target);
        return state;
    }
}
