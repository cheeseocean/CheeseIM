package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.UserConversationSyncPoint;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.UserConversationSyncPointDoc;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.BulkOperations;
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

    private final MongoTemplate mongoTemplate;

    public UserConversationSyncPointRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void updateReadSeq(String userId, String conversationId, long readSeq) {
        String id = docId(userId, conversationId);
        // readSeq 只允许前进，避免异步乱序写把已读水位回退。
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(id),
                new Criteria().orOperator(
                        Criteria.where("readSeq").lt(readSeq),
                        Criteria.where("readSeq").exists(false)
                )
        ));
        Update update = new Update()
                .setOnInsert("_id", id)
                .setOnInsert("userId", userId)
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("maxSeq", 0L)
                .setOnInsert("minSeq", 0L)
                .set("readSeq", readSeq);
        mongoTemplate.upsert(query, update, UserConversationSyncPointDoc.class);
    }

    @Override
    public long getMaxSeq(String userId, String conversationId) {
        return find(userId, conversationId)
                .map(UserConversationSyncPoint::getMaxSeq)
                .orElse(0L);
    }

    @Override
    public void updateMaxSeq(String userId, String conversationId, long maxSeq) {
        updateMaxSeqBatch(List.of(new MaxSeqUpdate(userId, conversationId, maxSeq)));
    }

    @Override
    public void updateMaxSeqBatch(List<MaxSeqUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        BulkOperations bulk = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED, UserConversationSyncPointDoc.class);
        int accepted = 0;
        for (MaxSeqUpdate update : updates) {
            if (update == null || update.userId() == null || update.conversationId() == null) {
                continue;
            }
            String id = docId(update.userId(), update.conversationId());
            Query query = Query.query(Criteria.where("_id").is(id));
            Update mongoUpdate = new Update()
                    .setOnInsert("_id", id)
                    .setOnInsert("userId", update.userId())
                    .setOnInsert("conversationId", update.conversationId())
                    .setOnInsert("minSeq", 0L)
                    .setOnInsert("readSeq", 0L)
                    .max("maxSeq", update.maxSeq());
            bulk.upsert(query, mongoUpdate);
            accepted++;
        }
        if (accepted > 0) {
            bulk.execute();
        }
    }

    @Override
    public long getMinSeq(String userId, String conversationId) {
        return find(userId, conversationId)
                .map(UserConversationSyncPoint::getMinSeq)
                .orElse(0L);
    }

    @Override
    public void updateMinSeq(String userId, String conversationId, long minSeq) {
        upsertSeqField(userId, conversationId, "minSeq", minSeq, "maxSeq", 0L, "readSeq", 0L);
    }

    @Override
    public long getReadSeq(String userId, String conversationId) {
        return find(userId, conversationId)
                .map(UserConversationSyncPoint::getReadSeq)
                .orElse(0L);
    }

    @Override
    public Map<String, Long> getReadSeqMap(String userId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<String> dedupedIds = dedupeConversationIds(conversationIds);
        if (dedupedIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        // 缺失记录按 0L 填充，调用方不需要再自己补默认值。
        Map<String, Long> readSeqMap = dedupedIds.stream()
                .collect(Collectors.toMap(
                        conversationId -> conversationId,
                        conversationId -> 0L,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Query query = Query.query(Criteria.where("userId").is(userId).and("conversationId").in(dedupedIds));
        mongoTemplate.find(query, UserConversationSyncPointDoc.class)
                .forEach(doc -> readSeqMap.put(doc.getConversationId(), doc.getReadSeq()));
        return readSeqMap;
    }

    @Override
    public Optional<UserConversationSyncPoint> find(String userId, String conversationId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("conversationId").is(conversationId));
        return Optional.ofNullable(mongoTemplate.findOne(query, UserConversationSyncPointDoc.class))
                .map(this::toDomain);
    }

    @Override
    public List<UserConversationSyncPoint> findByIds(String userId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> dedupedIds = dedupeConversationIds(conversationIds);
        if (dedupedIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 保留入参顺序，便于上层按原会话列表回填未读/水位信息。
        Query query = Query.query(Criteria.where("userId").is(userId).and("conversationId").in(dedupedIds));
        Map<String, UserConversationSyncPoint> syncPointMap = mongoTemplate.find(query, UserConversationSyncPointDoc.class)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toMap(
                        UserConversationSyncPoint::getConversationId,
                        syncPoint -> syncPoint,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return dedupedIds.stream()
                .map(syncPointMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<UserConversationSyncPoint> findByUserId(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        return mongoTemplate.find(query, UserConversationSyncPointDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void upsertSeqField(
            String userId,
            String conversationId,
            String seqField,
            long seq,
            String companionFieldA,
            long companionValueA,
            String companionFieldB,
            long companionValueB) {
        String id = docId(userId, conversationId);
        // 任何单一水位写入都顺手补齐其余默认字段，保证文档结构稳定。
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id", id)
                .setOnInsert("userId", userId)
                .setOnInsert("conversationId", conversationId)
                .setOnInsert(companionFieldA, companionValueA)
                .setOnInsert(companionFieldB, companionValueB)
                .set(seqField, seq);
        mongoTemplate.upsert(query, update, UserConversationSyncPointDoc.class);
    }

    private UserConversationSyncPoint toDomain(UserConversationSyncPointDoc doc) {
        UserConversationSyncPoint syncPoint = new UserConversationSyncPoint();
        syncPoint.setId(doc.getId());
        syncPoint.setUserId(doc.getUserId());
        syncPoint.setConversationId(doc.getConversationId());
        syncPoint.setReadSeq(doc.getReadSeq());
        syncPoint.setMaxSeq(doc.getMaxSeq());
        syncPoint.setMinSeq(doc.getMinSeq());
        return syncPoint;
    }

    private static List<String> dedupeConversationIds(List<String> conversationIds) {
        return conversationIds.stream()
                .filter(Objects::nonNull)
                .filter(conversationId -> !conversationId.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
    }

    private static String docId(String userId, String conversationId) {
        return userId + ":" + conversationId;
    }
}
