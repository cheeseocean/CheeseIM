package com.cheeseocean.im.social.repository;

import com.cheeseocean.im.social.domain.UserConversationStateDoc;
import com.cheeseocean.im.social.model.Conversation;
import com.cheeseocean.im.common.core.enums.SessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MongoConversationStoreTest {

    private MongoTemplate mongoTemplate;
    private MongoConversationStore store;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        store = new MongoConversationStore(mongoTemplate);
    }

    @Test
    void createIfAbsentShouldUpsertWithSetOnInsertOnly() {
        Conversation conv = conversation("alice", "c1:alice:bob", SessionType.SINGLE.getCode(), "bob");

        store.createIfAbsent(conv);

        verify(mongoTemplate).upsert(
                any(Query.class),
                any(Update.class),
                eq(UserConversationStateDoc.class));
    }

    @Test
    void updateLatestMessageShouldSetSeqAndJson() {
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        store.updateLatestMessage("alice", "c1:alice:bob", 42L, "{\"seq\":42}");

        verify(mongoTemplate).upsert(
                any(Query.class),
                updateCaptor.capture(),
                eq(UserConversationStateDoc.class));

        String updateJson = updateCaptor.getValue().toString();
        assertNotNull(updateJson);
        assert updateJson.contains("latestMsgSeq");
        assert updateJson.contains("latestMsg");
    }

    @Test
    void incrementUnreadShouldIncDelta() {
        store.incrementUnread("alice", "c1:alice:bob", 3);

        verify(mongoTemplate).upsert(
                any(Query.class),
                any(Update.class),
                eq(UserConversationStateDoc.class));
    }

    @Test
    void clearUnreadShouldUseUpdateFirst() {
        store.clearUnread("alice", "c1:alice:bob", 100L);

        verify(mongoTemplate).updateFirst(
                any(Query.class),
                any(Update.class),
                eq(UserConversationStateDoc.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Conversation conversation(String owner, String convId, int type, String target) {
        Conversation c = new Conversation();
        c.setOwnerUserId(owner);
        c.setConversationId(convId);
        c.setConversationType(type);
        c.setTargetId(target);
        return c;
    }
}
