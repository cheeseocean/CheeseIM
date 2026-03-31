package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.domain.UserConversationSyncPoint;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.UserConversationSyncPointDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.UserConversationSyncPointMongoRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link UserConversationSyncPointRepository} 的 MongoDB 实现。
 */
public class UserConversationSyncPointRepositoryImpl implements UserConversationSyncPointRepository {

    private final UserConversationSyncPointMongoRepository mongoRepository;
    private final MongoTemplate mongoTemplate;

    public UserConversationSyncPointRepositoryImpl(
            UserConversationSyncPointMongoRepository mongoRepository,
            MongoTemplate mongoTemplate) {
        this.mongoRepository = mongoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // ── 写操作 ────────────────────────────────────────────────────────────────

    @Override
    public void createIfAbsent(String userId, String conversationId) {
        String id = docId(userId, conversationId);
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id",            id)
                .setOnInsert("userId",          userId)
                .setOnInsert("conversationId",  conversationId)
                .setOnInsert("maxSeq",          0L)
                .setOnInsert("minSeq",          0L)
                .setOnInsert("readSeq",         0L);
        mongoTemplate.upsert(query, update, UserConversationSyncPointDoc.class);
    }

    @Override
    public void updateReadSeq(String userId, String conversationId, long readSeq) {
        Query query = Query.query(Criteria.where("_id").is(docId(userId, conversationId)));
        mongoTemplate.updateFirst(query, new Update().set("readSeq", readSeq),
                UserConversationSyncPointDoc.class);
    }

    @Override
    public void updateMaxSeq(String userId, String conversationId, long maxSeq) {
        String id = docId(userId, conversationId);
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id",           id)
                .setOnInsert("userId",         userId)
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("minSeq",         0L)
                .setOnInsert("readSeq",        0L)
                .set("maxSeq", maxSeq);
        mongoTemplate.upsert(query, update, UserConversationSyncPointDoc.class);
    }

    @Override
    public void updateMinSeq(String userId, String conversationId, long minSeq) {
        Query query = Query.query(Criteria.where("_id").is(docId(userId, conversationId)));
        mongoTemplate.updateFirst(query, new Update().set("minSeq", minSeq),
                UserConversationSyncPointDoc.class);
    }

    // ── 读操作 ────────────────────────────────────────────────────────────────

    @Override
    public Optional<UserConversationSyncPoint> find(String userId, String conversationId) {
        return mongoRepository.findByUserIdAndConversationId(userId, conversationId)
                .map(this::toDomain);
    }

    @Override
    public List<UserConversationSyncPoint> findByIds(String userId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return List.of();
        }

        List<String> dedupedIds = conversationIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
        if (dedupedIds.isEmpty()) {
            return List.of();
        }

        Map<String, UserConversationSyncPoint> ranges = mongoRepository
                .findByUserIdAndConversationIdIn(userId, dedupedIds)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toMap(
                        UserConversationSyncPoint::getConversationId,
                        range -> range,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return dedupedIds.stream().map(ranges::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public List<UserConversationSyncPoint> findByUserId(String userId) {
        return mongoRepository.findByUserId(userId)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    // ── 转换方法 ─────────────────────────────────────────────────────────────

    private UserConversationSyncPoint toDomain(UserConversationSyncPointDoc doc) {
        UserConversationSyncPoint range = new UserConversationSyncPoint();
        range.setId(doc.getId());
        range.setUserId(doc.getUserId());
        range.setConversationId(doc.getConversationId());
        range.setMaxSeq(doc.getMaxSeq());
        range.setMinSeq(doc.getMinSeq());
        range.setReadSeq(doc.getReadSeq());
        return range;
    }

    private static String docId(String userId, String conversationId) {
        return userId + ":" + conversationId;
    }
}
