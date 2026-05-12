package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.UserConversationDoc;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link UserConversationRepository} 的 MongoDB 实现。
 */
public class UserConversationRepositoryImpl implements UserConversationRepository {

    private final MongoTemplate mongoTemplate;

    public UserConversationRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void createIfAbsent(UserConversation conversation) {
        // 只在首次建会话时写入默认状态，避免覆盖用户后续的个性化配置。
        String id = docId(conversation.getOwnerUserId(), conversation.getConversationId());
        Instant now = now(conversation.getCreatedAt(), conversation.getUpdatedAt());
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id", id)
                .setOnInsert("ownerUserId", conversation.getOwnerUserId())
                .setOnInsert("conversationId", conversation.getConversationId())
                .setOnInsert("conversationType", conversation.getChatType())
                .setOnInsert("targetId", conversation.getTargetId())
                .setOnInsert("receiveOpt", conversation.getReceiveOpt())
                .setOnInsert("unreadCount", conversation.getUnreadCount())
                .setOnInsert("pinned", conversation.isPinned())
                .setOnInsert("attachedInfo", conversation.getAttachedInfo())
                .setOnInsert("groupAtType", conversation.getGroupAtType())
                .setOnInsert("autoCleanup", conversation.isAutoCleanup())
                .setOnInsert("autoCleanupCycle", conversation.getCleanupCycle())
                .setOnInsert("latestCleanupTime", conversation.getLatestCleanupTime())
                .setOnInsert("createdAt", now)
                .setOnInsert("updatedAt", now);
        mongoTemplate.upsert(query, update, UserConversationDoc.class);
    }

    @Override
    public void saveAll(List<UserConversation> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return;
        }
        // 用无序批量 upsert 保持批量写入的幂等性，单条冲突不会阻塞整批。
        BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UserConversationDoc.class);
        for (UserConversation conversation : conversations) {
            if (conversation == null
                    || isBlank(conversation.getOwnerUserId())
                    || isBlank(conversation.getConversationId())) {
                continue;
            }
            bulkOps.upsert(
                    Query.query(Criteria.where("_id").is(docId(conversation.getOwnerUserId(), conversation.getConversationId()))),
                    toUpsert(conversation)
            );
        }
        try {
            bulkOps.execute();
        } catch (BulkOperationException ignored) {
        }
    }

    @Override
    public void updateFields(String ownerUserId, String conversationId, Map<String, Object> fields) {
        if (isBlank(ownerUserId) || isBlank(conversationId) || fields == null || fields.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        Update update = new Update();
        fields.forEach((key, value) -> {
            if (key != null) {
                update.set(key, value);
            }
        });
        update.set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query, update, UserConversationDoc.class);
    }

    @Override
    public void updateBatchFields(List<String> ownerUserIds, String conversationId, Map<String, Object> fields) {
        if (ownerUserIds == null || ownerUserIds.isEmpty() || isBlank(conversationId) || fields == null || fields.isEmpty()) {
            return;
        }
        List<String> docIds = ownerUserIds.stream()
                .filter(Objects::nonNull)
                .filter(ownerUserId -> !ownerUserId.isBlank())
                .map(ownerUserId -> docId(ownerUserId, conversationId))
                .collect(Collectors.toCollection(ArrayList::new));
        if (docIds.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").in(docIds));
        Update update = new Update();
        fields.forEach((key, value) -> {
            if (key != null) {
                update.set(key, value);
            }
        });
        update.set("updatedAt", Instant.now());
        mongoTemplate.updateMulti(query, update, UserConversationDoc.class);
    }

    @Override
    public UserConversation findOne(String ownerUserId, String conversationId) {
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        UserConversationDoc doc = mongoTemplate.findOne(query, UserConversationDoc.class);
        return doc == null ? null : toDomain(doc);
    }

    @Override
    public List<UserConversation> findAll(String ownerUserId) {
        // 会话列表天然按最近活跃时间倒序读取。
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId))
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<UserConversation> findByIds(String ownerUserId, List<String> conversationIds) {
        if (isBlank(ownerUserId) || conversationIds == null || conversationIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> docIds = conversationIds.stream()
                .filter(Objects::nonNull)
                .filter(conversationId -> !conversationId.isBlank())
                .map(conversationId -> docId(ownerUserId, conversationId))
                .collect(Collectors.toCollection(ArrayList::new));
        if (docIds.isEmpty()) {
            return new ArrayList<>();
        }
        Query query = Query.query(Criteria.where("_id").in(docIds));
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<String> findConversationIds(String ownerUserId) {
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId));
        query.fields().include("conversationId");
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(UserConversationDoc::getConversationId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<String> findExistingOwnerUserIds(List<String> ownerUserIds, String conversationId) {
        if (ownerUserIds == null || ownerUserIds.isEmpty() || isBlank(conversationId)) {
            return new ArrayList<>();
        }
        List<String> docIds = ownerUserIds.stream()
                .filter(Objects::nonNull)
                .filter(ownerUserId -> !ownerUserId.isBlank())
                .map(ownerUserId -> docId(ownerUserId, conversationId))
                .collect(Collectors.toCollection(ArrayList::new));
        if (docIds.isEmpty()) {
            return new ArrayList<>();
        }
        Query query = Query.query(Criteria.where("_id").in(docIds));
        query.fields().include("ownerUserId");
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(UserConversationDoc::getOwnerUserId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<String> findNotReceiveUserIds(String conversationId, List<String> candidateUserIds) {
        if (isBlank(conversationId) || candidateUserIds == null || candidateUserIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> docIds = candidateUserIds.stream()
                .filter(Objects::nonNull)
                .filter(candidateUserId -> !candidateUserId.isBlank())
                .map(candidateUserId -> docId(candidateUserId, conversationId))
                .collect(Collectors.toCollection(ArrayList::new));
        if (docIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 这里直接按 receiveOpt 过滤，不再额外透传领域枚举，保持批量过滤路径最轻。
        Query query = Query.query(
                Criteria.where("_id").in(docIds).and("receiveOpt").is(1)
        );
        query.fields().include("ownerUserId");
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(UserConversationDoc::getOwnerUserId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<String> findAllNotReceiveUserIds(String conversationId) {
        Query query = Query.query(
                Criteria.where("conversationId").is(conversationId).and("receiveOpt").is(1)
        );
        query.fields().include("ownerUserId");
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(UserConversationDoc::getOwnerUserId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<String> findNotNotifyConversationIds(String ownerUserId) {
        Query query = Query.query(
                Criteria.where("ownerUserId").is(ownerUserId).and("receiveOpt").is(2)
        ).with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        query.fields().include("conversationId");
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(UserConversationDoc::getConversationId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<String> findPinnedConversationIds(String ownerUserId) {
        // 置顶列表仍按最近更新时间排序，便于直接用于客户端展示。
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId).and("pinned").is(true))
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        query.fields().include("conversationId");
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(UserConversationDoc::getConversationId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Update toUpsert(UserConversation conversation) {
        // createIfAbsent 负责默认初始化；这里负责完整状态回写。
        Instant createdAt = toInstant(conversation.getCreatedAt());
        Instant updatedAt = toInstant(conversation.getUpdatedAt());
        Update update = new Update()
                .set("ownerUserId", conversation.getOwnerUserId())
                .set("conversationId", conversation.getConversationId())
                .set("conversationType", conversation.getChatType())
                .set("targetId", conversation.getTargetId())
                .set("receiveOpt", conversation.getReceiveOpt())
                .set("unreadCount", conversation.getUnreadCount())
                .set("pinned", conversation.isPinned())
                .set("attachedInfo", conversation.getAttachedInfo())
                .set("groupAtType", conversation.getGroupAtType())
                .set("autoCleanup", conversation.isAutoCleanup())
                .set("autoCleanupCycle", conversation.getCleanupCycle())
                .set("latestCleanupTime", conversation.getLatestCleanupTime())
                .set("updatedAt", updatedAt != null ? updatedAt : Instant.now());
        if (createdAt != null) {
            update.setOnInsert("createdAt", createdAt);
        }
        return update;
    }

    private UserConversation toDomain(UserConversationDoc doc) {
        UserConversation conversation = new UserConversation();
        conversation.setOwnerUserId(doc.getOwnerUserId());
        conversation.setConversationId(doc.getConversationId());
        conversation.setChatType(doc.getChatType());
        conversation.setTargetId(doc.getTargetId());
        conversation.setReceiveOpt(doc.getReceiveOpt());
        conversation.setPinned(doc.isPinned());
        conversation.setAttachedInfo(doc.getAttachedInfo());
        conversation.setGroupAtType(doc.getGroupAtType());
        conversation.setAutoCleanup(doc.isAutoCleanup());
        conversation.setCleanupCycle(doc.getAutoCleanupCycle());
        conversation.setLatestCleanupTime(doc.getLatestCleanupTime());
        conversation.setCreatedAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toEpochMilli() : 0L);
        conversation.setUpdatedAt(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toEpochMilli() : 0L);
        return conversation;
    }

    private static String docId(String ownerUserId, String conversationId) {
        return ownerUserId + ":" + conversationId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Instant toInstant(long epochMilli) {
        return epochMilli > 0 ? Instant.ofEpochMilli(epochMilli) : null;
    }

    private static Instant now(long createdAt, long updatedAt) {
        Instant updated = toInstant(updatedAt);
        if (updated != null) {
            return updated;
        }
        Instant created = toInstant(createdAt);
        return created != null ? created : Instant.now();
    }
}
