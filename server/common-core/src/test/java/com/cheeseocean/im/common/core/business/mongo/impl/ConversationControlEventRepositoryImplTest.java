package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.enums.ControlEventTypeEnum;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.ConversationControlEventCursorDoc;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.ConversationControlEventDoc;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

class ConversationControlEventRepositoryImplTest {

    @Test
    void partitionTargetsShouldKeepEachUserInOneCursorShardAndBoundDocumentSize() {
        List<String> targets = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            targets.add("user-" + i);
        }
        targets.add("user-1");

        List<List<String>> partitions = ConversationControlEventRepositoryImpl.partitionTargets(targets);

        Set<String> flattened = new HashSet<>();
        for (List<String> partition : partitions) {
            assertTrue(partition.size() <= ConversationControlEventRepositoryImpl.MAX_TARGETS_PER_EVENT);
            int shard = ConversationControlEventRepositoryImpl.cursorShard(partition.get(0));
            assertTrue(partition.stream().allMatch(target ->
                    ConversationControlEventRepositoryImpl.cursorShard(target) == shard));
            flattened.addAll(partition);
        }
        assertEquals(20_000, flattened.size());
    }

    @Test
    void encodedShardCursorShouldRemainAboveLegacyGlobalCursorAndMonotonicForUser() {
        long legacyCursor = 1_000_000L;
        int shard = ConversationControlEventRepositoryImpl.cursorShard("user-42");

        long first = ConversationControlEventRepositoryImpl.encodeCursor(legacyCursor + 1L, shard);
        long second = ConversationControlEventRepositoryImpl.encodeCursor(legacyCursor + 2L, shard);

        assertTrue(first > legacyCursor);
        assertTrue(second > first);
    }

    @Test
    void partitionEventIdShouldBeDeterministicAcrossTargetOrdering() {
        List<String> targets = usersInSameShard(3);
        List<String> reversed = new ArrayList<>(targets);
        java.util.Collections.reverse(reversed);
        List<String> canonical = new ArrayList<>(targets);
        canonical.sort(String::compareTo);
        reversed.sort(String::compareTo);

        String first = ConversationControlEventRepositoryImpl.partitionEventId("revoke:mutation-1", canonical);
        String retried = ConversationControlEventRepositoryImpl.partitionEventId("revoke:mutation-1", reversed);

        assertEquals(first, retried);
        assertNotEquals(first, ConversationControlEventRepositoryImpl.partitionEventId("revoke:mutation-2", canonical));
    }

    @Test
    void publicAppendShouldRejectMissingIdCrossShardAndOversizedTargets() {
        ConversationControlEventRepositoryImpl repository =
                new ConversationControlEventRepositoryImpl(mock(MongoTemplate.class));
        ConversationControlEvent missingId = event(usersInSameShard(1));
        assertThrows(IllegalArgumentException.class, () -> repository.append(missingId));

        ConversationControlEvent crossShard = event(usersInDifferentShards());
        crossShard.setEventId("event-cross-shard");
        assertThrows(IllegalArgumentException.class, () -> repository.append(crossShard));

        ConversationControlEvent oversized = event(usersInSameShard(
                ConversationControlEventRepositoryImpl.MAX_TARGETS_PER_EVENT + 1));
        oversized.setEventId("event-oversized");
        assertThrows(IllegalArgumentException.class, () -> repository.append(oversized));
    }

    @Test
    void appendPartitionedShouldResumeAfterPartialMongoFailureWithoutDuplicatingSavedPartition() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Map<String, ConversationControlEventDoc> stored = new java.util.concurrent.ConcurrentHashMap<>();
        AtomicLong cursor = new AtomicLong(10L);
        AtomicBoolean failSecondInsertOnce = new AtomicBoolean(true);
        AtomicLong eventInsertAttempts = new AtomicLong();
        when(mongo.findById(anyString(), eq(ConversationControlEventCursorDoc.class))).thenReturn(null);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(),
                eq(ConversationControlEventCursorDoc.class))).thenAnswer(invocation -> {
            ConversationControlEventCursorDoc doc = new ConversationControlEventCursorDoc();
            doc.setCursor(cursor.incrementAndGet());
            return doc;
        });
        when(mongo.findById(anyString(), eq(ConversationControlEventDoc.class)))
                .thenAnswer(invocation -> stored.get(invocation.getArgument(0)));
        when(mongo.upsert(any(Query.class), any(Update.class), eq(ConversationControlEventCursorDoc.class)))
                .thenReturn(mock(UpdateResult.class));
        when(mongo.upsert(any(Query.class), any(Update.class), eq(ConversationControlEventDoc.class)))
                .thenAnswer(invocation -> {
                    if (eventInsertAttempts.incrementAndGet() == 2L && failSecondInsertOnce.getAndSet(false)) {
                        throw new IllegalStateException("simulated second partition failure");
                    }
                    Query query = invocation.getArgument(0);
                    Update update = invocation.getArgument(1);
                    String id = query.getQueryObject().getString("_id");
                    stored.computeIfAbsent(id, ignored -> eventDoc(update.getUpdateObject()));
                    return mock(UpdateResult.class);
                });
        ConversationControlEventRepositoryImpl repository = new ConversationControlEventRepositoryImpl(mongo);
        ConversationControlEvent logical = event(usersInSameShard(201));
        logical.setEventId("revoke:partial-failure");

        assertThrows(IllegalStateException.class, () -> repository.appendPartitioned(logical));
        assertEquals(1, stored.size());
        long cursorAfterFailure = cursor.get();

        List<ConversationControlEvent> retried = repository.appendPartitioned(logical);

        assertEquals(2, retried.size());
        assertEquals(2, stored.size());
        assertEquals(cursorAfterFailure + 1L, cursor.get(),
                "重入不得为已存在分片再次分配 cursor");
        assertEquals(2, retried.stream().map(ConversationControlEvent::getEventId).distinct().count());
    }

    @Test
    void claimableIndexesShouldStartWithShardAndDeliveryState() {
        CompoundIndexes indexes = ConversationControlEventDoc.class.getAnnotation(CompoundIndexes.class);
        List<String> definitions = java.util.Arrays.stream(indexes.value())
                .map(index -> index.def().replace(" ", ""))
                .toList();

        assertTrue(definitions.stream().anyMatch(definition ->
                definition.startsWith("{'cursorShard':1,'deliveryStateCode':1,'cursor':1")));
        assertTrue(definitions.stream().anyMatch(definition ->
                definition.startsWith("{'cursorShard':1,'deliveryStateCode':1,'claimExpiresAt':1")));
    }

    @SuppressWarnings("unchecked")
    private static ConversationControlEventDoc eventDoc(Document updateObject) {
        Document values = (Document) updateObject.get("$setOnInsert");
        ConversationControlEventDoc doc = new ConversationControlEventDoc();
        doc.setId(values.getString("_id"));
        doc.setCursor(((Number) values.get("cursor")).longValue());
        doc.setCursorShard(((Number) values.get("cursorShard")).intValue());
        doc.setConversationId(values.getString("conversationId"));
        doc.setTypeCode(((Number) values.get("typeCode")).intValue());
        doc.setTargetUserIds((List<String>) values.get("targetUserIds"));
        doc.setPayload(values.getString("payload"));
        doc.setDeliveryStateCode(((Number) values.get("deliveryStateCode")).intValue());
        doc.setDeliveryAttempt(((Number) values.get("deliveryAttempt")).intValue());
        doc.setCreatedAt((java.time.Instant) values.get("createdAt"));
        doc.setExpiresAt((java.time.Instant) values.get("expiresAt"));
        return doc;
    }

    private static ConversationControlEvent event(List<String> targets) {
        ConversationControlEvent event = new ConversationControlEvent();
        event.setConversationId("g:test");
        event.setType(ControlEventTypeEnum.MESSAGE_REVOKED);
        event.setTargetUserIds(targets);
        event.setPayload("{}");
        event.setExpiresAt(System.currentTimeMillis() + 60_000L);
        return event;
    }

    private static List<String> usersInSameShard(int count) {
        List<String> result = new ArrayList<>();
        int shard = ConversationControlEventRepositoryImpl.cursorShard("seed-user");
        for (int i = 0; result.size() < count; i++) {
            String user = "same-shard-" + i;
            if (ConversationControlEventRepositoryImpl.cursorShard(user) == shard) result.add(user);
        }
        return result;
    }

    private static List<String> usersInDifferentShards() {
        String first = "cross-shard-0";
        int shard = ConversationControlEventRepositoryImpl.cursorShard(first);
        for (int i = 1; ; i++) {
            String candidate = "cross-shard-" + i;
            if (ConversationControlEventRepositoryImpl.cursorShard(candidate) != shard) {
                return List.of(first, candidate);
            }
        }
    }
}
