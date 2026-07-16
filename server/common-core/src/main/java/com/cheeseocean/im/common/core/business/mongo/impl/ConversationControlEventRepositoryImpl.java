package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.enums.ControlEventDeliveryStateEnum;
import com.cheeseocean.im.common.api.enums.ControlEventTypeEnum;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.ConversationControlEventCursorDoc;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.ConversationControlEventDoc;
import com.cheeseocean.im.common.core.business.repository.ConversationControlEventRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * {@link ConversationControlEventRepository} 的 MongoDB outbox 实现。
 */
@Repository
public class ConversationControlEventRepositoryImpl implements ConversationControlEventRepository {

    private static final int DEFAULT_QUERY_LIMIT = 200;
    private static final int MAX_QUERY_LIMIT = 500;
    static final int CURSOR_SHARD_COUNT = 64;
    static final int MAX_TARGETS_PER_EVENT = 200;

    private final MongoTemplate mongoTemplate;
    private final AtomicInteger claimScanStart = new AtomicInteger();

    public ConversationControlEventRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public ConversationControlEvent append(ConversationControlEvent event) {
        if (!isAppendable(event)) {
            return null;
        }
        validateSingleShardEvent(event);
        return appendIdempotently(event);
    }

    private ConversationControlEvent appendIdempotently(ConversationControlEvent event) {
        Instant now = Instant.now();
        String eventId = event.getEventId();
        ConversationControlEventDoc existing = mongoTemplate.findById(eventId, ConversationControlEventDoc.class);
        if (existing != null) {
            return toDomain(existing);
        }
        int cursorShard = cursorShard(event.getTargetUserIds().get(0));
        Query query = Query.query(Criteria.where("_id").is(eventId));
        Update insert = new Update()
                .setOnInsert("_id", eventId)
                .setOnInsert("cursor", nextCursor(cursorShard))
                .setOnInsert("cursorShard", cursorShard)
                .setOnInsert("conversationId", event.getConversationId())
                .setOnInsert("typeCode", event.getType().getCode())
                .setOnInsert("targetUserIds", new ArrayList<>(event.getTargetUserIds()))
                .setOnInsert("payload", event.getPayload())
                .setOnInsert("deliveryStateCode", ControlEventDeliveryStateEnum.PENDING.getCode())
                .setOnInsert("deliveryAttempt", 0)
                .setOnInsert("createdAt", now)
                .setOnInsert("expiresAt", Instant.ofEpochMilli(event.getExpiresAt()));
        mongoTemplate.upsert(query, insert, ConversationControlEventDoc.class);
        ConversationControlEventDoc stored = mongoTemplate.findById(eventId, ConversationControlEventDoc.class);
        if (stored == null) {
            throw new IllegalStateException("Control event upsert completed but document is missing: " + eventId);
        }
        return toDomain(stored);
    }

    @Override
    public List<ConversationControlEvent> appendPartitioned(ConversationControlEvent event) {
        if (!isAppendable(event)) {
            return new ArrayList<>();
        }
        String logicalEventId = isBlank(event.getEventId()) ? UUID.randomUUID().toString() : event.getEventId();
        event.setEventId(logicalEventId);
        List<ConversationControlEvent> appended = new ArrayList<>();
        for (List<String> targets : partitionTargets(event.getTargetUserIds())) {
            ConversationControlEvent partition = copyWithTargets(event, targets);
            partition.setEventId(partitionEventId(logicalEventId, targets));
            ConversationControlEvent saved = append(partition);
            if (saved != null) {
                appended.add(saved);
            }
        }
        return appended;
    }

    @Override
    public List<ConversationControlEvent> findAfter(String targetUserId, long cursor, int limit) {
        if (isBlank(targetUserId)) {
            return new ArrayList<>();
        }
        int effectiveLimit = limit <= 0 ? DEFAULT_QUERY_LIMIT : Math.min(limit, MAX_QUERY_LIMIT);
        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("targetUserIds").is(targetUserId),
                        Criteria.where("cursor").gt(Math.max(cursor, 0L)),
                        Criteria.where("expiresAt").gt(Instant.now())
                ))
                .with(Sort.by(Sort.Direction.ASC, "cursor"))
                .limit(effectiveLimit);
        return mongoTemplate.find(query, ConversationControlEventDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<ConversationControlEvent> findClaimable(int limit) {
        int effectiveLimit = limit <= 0 ? DEFAULT_QUERY_LIMIT : Math.min(limit, MAX_QUERY_LIMIT);
        Instant now = Instant.now();
        List<ConversationControlEventDoc> candidates = new ArrayList<>(effectiveLimit);
        int firstShard = Math.floorMod(claimScanStart.getAndIncrement(), CURSOR_SHARD_COUNT);
        for (int offset = 0; offset < CURSOR_SHARD_COUNT && candidates.size() < effectiveLimit; offset++) {
            int shard = (firstShard + offset) % CURSOR_SHARD_COUNT;
            findClaimableInShard(shard, ControlEventDeliveryStateEnum.PENDING, now,
                    effectiveLimit - candidates.size(), candidates);
            if (candidates.size() < effectiveLimit) {
                findClaimableInShard(shard, ControlEventDeliveryStateEnum.CLAIMED, now,
                        effectiveLimit - candidates.size(), candidates);
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingLong(ConversationControlEventDoc::getCursor))
                .limit(effectiveLimit)
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void findClaimableInShard(int shard, ControlEventDeliveryStateEnum state, Instant now, int limit,
                                      List<ConversationControlEventDoc> destination) {
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("cursorShard").is(shard),
                Criteria.where("deliveryStateCode").is(state.getCode()),
                Criteria.where("expiresAt").gt(now));
        if (state == ControlEventDeliveryStateEnum.CLAIMED) {
            criteria = new Criteria().andOperator(criteria, Criteria.where("claimExpiresAt").lte(now));
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.ASC, "cursor"))
                .limit(limit);
        destination.addAll(mongoTemplate.find(query, ConversationControlEventDoc.class));
    }

    @Override
    public Optional<ConversationControlEvent> claim(String eventId, long claimLeaseMillis) {
        if (isBlank(eventId) || claimLeaseMillis <= 0) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        String claimToken = UUID.randomUUID().toString();
        Criteria claimable = new Criteria().orOperator(
                Criteria.where("deliveryStateCode").is(ControlEventDeliveryStateEnum.PENDING.getCode()),
                new Criteria().andOperator(
                        Criteria.where("deliveryStateCode").is(ControlEventDeliveryStateEnum.CLAIMED.getCode()),
                        Criteria.where("claimExpiresAt").lte(now)
                )
        );
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(eventId),
                Criteria.where("expiresAt").gt(now),
                claimable
        ));
        Update update = new Update()
                .set("deliveryStateCode", ControlEventDeliveryStateEnum.CLAIMED.getCode())
                .set("claimToken", claimToken)
                .set("claimExpiresAt", now.plusMillis(claimLeaseMillis))
                .inc("deliveryAttempt", 1);
        ConversationControlEventDoc claimed = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), ConversationControlEventDoc.class);
        return Optional.ofNullable(claimed).map(this::toDomain);
    }

    @Override
    public boolean markDelivered(String eventId, String claimToken) {
        if (isBlank(eventId) || isBlank(claimToken)) {
            return false;
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(eventId),
                Criteria.where("deliveryStateCode").is(ControlEventDeliveryStateEnum.CLAIMED.getCode()),
                Criteria.where("claimToken").is(claimToken)
        ));
        Update update = new Update()
                .set("deliveryStateCode", ControlEventDeliveryStateEnum.DELIVERED.getCode())
                .set("deliveredAt", Instant.now())
                .unset("claimToken")
                .unset("claimExpiresAt");
        return mongoTemplate.updateFirst(query, update, ConversationControlEventDoc.class).getModifiedCount() == 1;
    }

    private long nextCursor(int shardId) {
        String cursorId = ConversationControlEventCursorDoc.shardCursorId(shardId);
        ConversationControlEventCursorDoc legacy = mongoTemplate.findById(
                ConversationControlEventCursorDoc.GLOBAL_CURSOR_ID, ConversationControlEventCursorDoc.class);
        long legacyCursor = legacy == null ? 0L : Math.max(legacy.getCursor(), 0L);
        Query query = Query.query(Criteria.where("_id").is(cursorId));
        mongoTemplate.upsert(query, new Update()
                .setOnInsert("_id", cursorId)
                .setOnInsert("cursor", legacyCursor), ConversationControlEventCursorDoc.class);
        Update update = new Update().inc("cursor", 1L);
        ConversationControlEventCursorDoc cursorDoc = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                ConversationControlEventCursorDoc.class
        );
        long localCursor = cursorDoc == null ? legacyCursor + 1L : cursorDoc.getCursor();
        return encodeCursor(localCursor, shardId);
    }

    private ConversationControlEvent toDomain(ConversationControlEventDoc doc) {
        ConversationControlEvent event = new ConversationControlEvent();
        event.setEventId(doc.getId());
        event.setCursor(doc.getCursor());
        event.setConversationId(doc.getConversationId());
        event.setType(ControlEventTypeEnum.fromCode(doc.getTypeCode()));
        event.setTargetUserIds(doc.getTargetUserIds() == null ? new ArrayList<>() : new ArrayList<>(doc.getTargetUserIds()));
        event.setPayload(doc.getPayload());
        event.setDeliveryState(ControlEventDeliveryStateEnum.fromCode(doc.getDeliveryStateCode()));
        event.setDeliveryAttempt(doc.getDeliveryAttempt());
        event.setClaimToken(doc.getClaimToken());
        event.setCreatedAt(toEpochMilli(doc.getCreatedAt()));
        event.setExpiresAt(toEpochMilli(doc.getExpiresAt()));
        event.setClaimExpiresAt(toEpochMilli(doc.getClaimExpiresAt()));
        event.setDeliveredAt(toEpochMilli(doc.getDeliveredAt()));
        return event;
    }

    private static boolean isAppendable(ConversationControlEvent event) {
        return event != null
                && !isBlank(event.getConversationId())
                && event.getType() != null
                && event.getTargetUserIds() != null
                && !event.getTargetUserIds().isEmpty()
                && event.getTargetUserIds().stream().noneMatch(ConversationControlEventRepositoryImpl::isBlank)
                && event.getExpiresAt() > Instant.now().toEpochMilli();
    }

    static int cursorShard(String targetUserId) {
        return Math.floorMod(targetUserId.hashCode(), CURSOR_SHARD_COUNT);
    }

    static long encodeCursor(long localCursor, int shardId) {
        if (localCursor > (Long.MAX_VALUE - shardId) / CURSOR_SHARD_COUNT) {
            throw new IllegalStateException("Conversation control event cursor exhausted");
        }
        return localCursor * CURSOR_SHARD_COUNT + shardId;
    }

    static List<List<String>> partitionTargets(List<String> targetUserIds) {
        Map<Integer, List<String>> byShard = new LinkedHashMap<>();
        targetUserIds.stream().distinct().forEach(target ->
                byShard.computeIfAbsent(cursorShard(target), ignored -> new ArrayList<>()).add(target));
        List<List<String>> partitions = new ArrayList<>();
        for (List<String> shardTargets : byShard.values()) {
            shardTargets.sort(String::compareTo);
            for (int offset = 0; offset < shardTargets.size(); offset += MAX_TARGETS_PER_EVENT) {
                partitions.add(new ArrayList<>(shardTargets.subList(
                        offset, Math.min(offset + MAX_TARGETS_PER_EVENT, shardTargets.size()))));
            }
        }
        return partitions;
    }

    private static ConversationControlEvent copyWithTargets(ConversationControlEvent source, List<String> targets) {
        ConversationControlEvent copy = new ConversationControlEvent();
        copy.setConversationId(source.getConversationId());
        copy.setType(source.getType());
        copy.setTargetUserIds(targets);
        copy.setPayload(source.getPayload());
        copy.setExpiresAt(source.getExpiresAt());
        return copy;
    }

    private static void validateSingleShardEvent(ConversationControlEvent event) {
        if (isBlank(event.getEventId())) {
            throw new IllegalArgumentException("Single control event requires a stable eventId");
        }
        if (event.getTargetUserIds().size() > MAX_TARGETS_PER_EVENT) {
            throw new IllegalArgumentException("Single control event target count exceeds " + MAX_TARGETS_PER_EVENT);
        }
        int shard = cursorShard(event.getTargetUserIds().get(0));
        if (event.getTargetUserIds().stream().anyMatch(target -> cursorShard(target) != shard)) {
            throw new IllegalArgumentException("Single control event targets must belong to one cursor shard");
        }
    }

    static String partitionEventId(String logicalEventId, List<String> targets) {
        int shard = cursorShard(targets.get(0));
        String material = logicalEventId + "\n" + String.join("\n", targets);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return logicalEventId + ":p" + shard + ":" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static long toEpochMilli(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
