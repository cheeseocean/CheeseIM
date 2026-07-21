package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.ConversationVersionLog;
import com.cheeseocean.im.common.api.enums.ConversationVersionOperation;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.ConversationVersionLogDoc;
import com.cheeseocean.im.common.core.business.repository.ConversationVersionLogRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@link ConversationVersionLogRepository} 的 MongoDB 实现。
 */
@Repository
public class ConversationVersionLogRepositoryImpl implements ConversationVersionLogRepository {

    private final MongoTemplate mongoTemplate;

    public ConversationVersionLogRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public ConversationVersionLog append(String ownerUserId, String conversationId, ConversationVersionOperation operation) {
        if (isBlank(ownerUserId) || isBlank(conversationId) || operation == null) {
            return null;
        }
        Optional<ConversationVersionLog> latest = findLatest(ownerUserId);
        String versionId = latest.map(ConversationVersionLog::getVersionId)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        long nextVersion = latest.map(ConversationVersionLog::getVersion).orElse(0L) + 1L;

        ConversationVersionLogDoc doc = new ConversationVersionLogDoc();
        doc.setId(ownerUserId + ":" + nextVersion + ":" + UUID.randomUUID());
        doc.setOwnerUserId(ownerUserId);
        doc.setConversationId(conversationId);
        doc.setVersionId(versionId);
        doc.setVersion(nextVersion);
        doc.setOperation(operation);
        doc.setCreatedAt(Instant.now());
        mongoTemplate.insert(doc);
        return toDomain(doc);
    }

    @Override
    public Optional<ConversationVersionLog> findLatest(String ownerUserId) {
        if (isBlank(ownerUserId)) {
            return Optional.empty();
        }
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId))
                .with(Sort.by(Sort.Direction.DESC, "version"))
                .limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(query, ConversationVersionLogDoc.class))
                .map(this::toDomain);
    }

    @Override
    public List<ConversationVersionLog> findAfter(String ownerUserId, String versionId, long version, int limit) {
        if (isBlank(ownerUserId) || isBlank(versionId)) {
            return new ArrayList<>();
        }
        int effectiveLimit = limit <= 0 ? 200 : limit;
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId)
                        .and("versionId").is(versionId)
                        .and("version").gt(version))
                .with(Sort.by(Sort.Direction.ASC, "version"))
                .limit(effectiveLimit);
        return mongoTemplate.find(query, ConversationVersionLogDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ConversationVersionLog toDomain(ConversationVersionLogDoc doc) {
        ConversationVersionLog log = new ConversationVersionLog();
        log.setId(doc.getId());
        log.setOwnerUserId(doc.getOwnerUserId());
        log.setVersionId(doc.getVersionId());
        log.setVersion(doc.getVersion());
        log.setConversationId(doc.getConversationId());
        log.setOperation(doc.getOperation());
        log.setCreatedAt(doc.getCreatedAt() == null ? 0L : doc.getCreatedAt().toEpochMilli());
        return log;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
