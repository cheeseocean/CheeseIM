package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.domain.UserConversationSyncPoint;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.UserConversationSyncPointDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.UserConversationSyncPointMongoRepository;
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
import static org.mockito.Mockito.when;

class UserConversationSyncPointRepositoryImplTest {

    private MongoTemplate mongoTemplate;
    private UserConversationSyncPointMongoRepository mongoRepository;
    private UserConversationSyncPointRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        mongoRepository = mock(UserConversationSyncPointMongoRepository.class);
        repository = new UserConversationSyncPointRepositoryImpl(mongoRepository, mongoTemplate);
    }

    @Test
    void createIfAbsentShouldUpsertWithSetOnInsertOnly() {
        repository.createIfAbsent("alice", "c1");

        verify(mongoTemplate).upsert(
                any(Query.class),
                any(Update.class),
                eq(UserConversationSyncPointDoc.class));
    }

    @Test
    void updateReadSeqShouldPersistReadSeq() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        repository.updateReadSeq("alice", "c1", 42L);

        verify(mongoTemplate).updateFirst(
                any(Query.class),
                updateCaptor.capture(),
                eq(UserConversationSyncPointDoc.class));

        String updateJson = updateCaptor.getValue().getUpdateObject().toString();
        assertNotNull(updateJson);
        assertTrue(updateJson.contains("readSeq"));
    }

    @Test
    void updateMaxSeqShouldUpsertMaxSeq() {
        repository.updateMaxSeq("alice", "c1", 99L);

        verify(mongoTemplate).upsert(
                any(Query.class),
                any(Update.class),
                eq(UserConversationSyncPointDoc.class));
    }

    @Test
    void updateMinSeqShouldPersistMinSeq() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        repository.updateMinSeq("alice", "c1", 3L);

        verify(mongoTemplate).updateFirst(
                any(Query.class),
                updateCaptor.capture(),
                eq(UserConversationSyncPointDoc.class));

        String updateJson = updateCaptor.getValue().getUpdateObject().toString();
        assertNotNull(updateJson);
        assertTrue(updateJson.contains("minSeq"));
    }

    @Test
    void domainShouldComputeUnreadCount() {
        UserConversationSyncPoint checkpoint = new UserConversationSyncPoint();
        checkpoint.setReadSeq(4L);
        checkpoint.setMaxSeq(9L);

        assertTrue(checkpoint.getUnreadCount() == 5L);
    }
}
