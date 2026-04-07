package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.domain.UserConversation;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.UserConversationDoc;
import com.cheeseocean.im.common.api.enums.SessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserConversationRepositoryImplTest {

    private MongoTemplate mongoTemplate;
    private UserConversationRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        repository = new UserConversationRepositoryImpl(mongoTemplate);
    }

    @Test
    void createIfAbsentShouldUpsertWithSetOnInsertOnly() {
        UserConversation state = conversation("alice", "c1:alice:bob", SessionType.SINGLE.getCode(), "bob");

        repository.createIfAbsent(state);

        verify(mongoTemplate).upsert(
                any(Query.class),
                any(Update.class),
                eq(UserConversationDoc.class));
    }

    @Test
    void updateLatestMessageShouldSetSeqAndJson() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        repository.updateLatestMessage("alice", "c1:alice:bob", 42L, "{\"seq\":42}");

        verify(mongoTemplate).upsert(
                any(Query.class),
                updateCaptor.capture(),
                eq(UserConversationDoc.class));

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
                eq(UserConversationDoc.class));
    }

    @Test
    void clearUnreadShouldUseUpdateFirst() {
        repository.clearUnread("alice", "c1:alice:bob");

        verify(mongoTemplate).updateFirst(
                any(Query.class),
                any(Update.class),
                eq(UserConversationDoc.class));
    }

    private static UserConversation conversation(String owner, String convId, int type, String target) {
        UserConversation state = new UserConversation();
        state.setOwnerUserId(owner);
        state.setConversationId(convId);
        state.setConversationType(type);
        state.setTargetId(target);
        return state;
    }
}
