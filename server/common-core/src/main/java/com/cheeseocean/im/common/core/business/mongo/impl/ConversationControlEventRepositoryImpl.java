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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link ConversationControlEventRepository} 的 MongoDB outbox 实现。
 */
@Repository
public class ConversationControlEventRepositoryImpl implements ConversationControlEventRepository {

    private static final int DEFAULT_QUERY_LIMIT = 200;
    private static final int MAX_QUERY_LIMIT = 500;

    private final MongoTemplate mongoTemplate;

    public ConversationControlEventRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public ConversationControlEvent append(ConversationControlEvent event) {
        if (!isAppendable(event)) {
            return null;
        }
        Instant now = Instant.now();
        ConversationControlEventDoc doc = new ConversationControlEventDoc();
        doc.setId(isBlank(event.getEventId()) ? UUID.randomUUID().toString() : event.getEventId());
        doc.setCursor(nextCursor());
        doc.setConversationId(event.getConversationId());
        doc.setTypeCode(event.getType().getCode());
        doc.setTargetUserIds(new ArrayList<>(event.getTargetUserIds()));
        doc.setPayload(event.getPayload());
        doc.setDeliveryStateCode(ControlEventDeliveryStateEnum.PENDING.getCode());
        doc.setDeliveryAttempt(0);
        doc.setCreatedAt(now);
        doc.setExpiresAt(Instant.ofEpochMilli(event.getExpiresAt()));
        mongoTemplate.insert(doc);
        return toDomain(doc);
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
        Criteria claimable = new Criteria().orOperator(
                Criteria.where("deliveryStateCode").is(ControlEventDeliveryStateEnum.PENDING.getCode()),
                new Criteria().andOperator(
                        Criteria.where("deliveryStateCode").is(ControlEventDeliveryStateEnum.CLAIMED.getCode()),
                        Criteria.where("claimExpiresAt").lte(now)
                )
        );
        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("expiresAt").gt(now),
                        claimable
                ))
                .with(Sort.by(Sort.Direction.ASC, "cursor"))
                .limit(effectiveLimit);
        return mongoTemplate.find(query, ConversationControlEventDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
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

    private long nextCursor() {
        Query query = Query.query(Criteria.where("_id").is(ConversationControlEventCursorDoc.GLOBAL_CURSOR_ID));
        Update update = new Update()
                .setOnInsert("_id", ConversationControlEventCursorDoc.GLOBAL_CURSOR_ID)
                .inc("cursor", 1L);
        ConversationControlEventCursorDoc cursorDoc = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                ConversationControlEventCursorDoc.class
        );
        return cursorDoc == null ? 0L : cursorDoc.getCursor();
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

    private static long toEpochMilli(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
