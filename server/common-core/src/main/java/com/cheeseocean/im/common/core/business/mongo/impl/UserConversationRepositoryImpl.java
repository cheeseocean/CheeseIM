package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.domain.UserConversation;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.UserConversationDoc;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link UserConversationRepository} 的 MongoDB 实现。
 */
public class UserConversationRepositoryImpl implements UserConversationRepository {

    private final MongoTemplate mongoTemplate;

    public UserConversationRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // ── 写操作 ────────────────────────────────────────────────────────────────

    @Override
    public void createIfAbsent(UserConversation state) {
        String id = docId(state.getOwnerUserId(), state.getConversationId());
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id",             id)
                .setOnInsert("ownerUserId",      state.getOwnerUserId())
                .setOnInsert("conversationId",   state.getConversationId())
                .setOnInsert("conversationType", state.getConversationType())
                .setOnInsert("targetId",         state.getTargetId())
                .setOnInsert("recvMsgOpt",       state.getRecvMsgOpt())
                .setOnInsert("unreadCount",      0)
                .setOnInsert("pinned",           false)
                .setOnInsert("createdAt",        Instant.now());
        mongoTemplate.upsert(query, update, UserConversationDoc.class);
    }

    @Override
    public void updateLatestMessage(String ownerUserId, String conversationId,
                                    long latestMsgSeq, String latestMsgJson) {
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        Update update = new Update()
                .set("latestMsgSeq", latestMsgSeq)
                .set("latestMsg",    latestMsgJson)
                .set("updatedAt",    Instant.now());
        mongoTemplate.upsert(query, update, UserConversationDoc.class);
    }

    @Override
    public void incrementUnread(String ownerUserId, String conversationId, int delta) {
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        Update update = new Update()
                .inc("unreadCount", delta)
                .set("updatedAt",   Instant.now());
        mongoTemplate.upsert(query, update, UserConversationDoc.class);
    }

    @Override
    public void clearUnread(String ownerUserId, String conversationId) {
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        Update update = new Update()
                .set("unreadCount", 0)
                .set("updatedAt",   Instant.now());
        mongoTemplate.updateFirst(query, update, UserConversationDoc.class);
    }

    @Override
    public void setRecvMsgOpt(String ownerUserId, String conversationId, int recvMsgOpt) {
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        Update update = new Update()
                .set("recvMsgOpt", recvMsgOpt)
                .set("updatedAt",  Instant.now());
        mongoTemplate.upsert(query, update, UserConversationDoc.class);
    }

    @Override
    public void upsertFields(String ownerUserId, String conversationId,
                             int conversationType, String targetId,
                             Map<String, Object> fields) {
        String id = docId(ownerUserId, conversationId);
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id",             id)
                .setOnInsert("ownerUserId",      ownerUserId)
                .setOnInsert("conversationId",   conversationId)
                .setOnInsert("conversationType", conversationType)
                .setOnInsert("targetId",         targetId)
                .setOnInsert("unreadCount",      0)
                .setOnInsert("pinned",           false)
                .setOnInsert("createdAt",        Instant.now());
        fields.forEach(update::set);
        update.set("updatedAt", Instant.now());
        mongoTemplate.upsert(query, update, UserConversationDoc.class);
    }

    // ── 读操作 ────────────────────────────────────────────────────────────────

    @Override
    public int getRecvMsgOpt(String ownerUserId, String conversationId) {
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        query.fields().include("recvMsgOpt");
        UserConversationDoc doc = mongoTemplate.findOne(query, UserConversationDoc.class);
        return doc == null ? 0 : doc.getRecvMsgOpt();
    }

    @Override
    public UserConversation findOne(String ownerUserId, String conversationId) {
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        UserConversationDoc doc = mongoTemplate.findOne(query, UserConversationDoc.class);
        return doc == null ? null : toDomain(doc);
    }

    @Override
    public List<UserConversation> findAll(String ownerUserId) {
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId))
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        return mongoTemplate.find(query, UserConversationDoc.class)
                .stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<UserConversation> findByIds(String ownerUserId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return List.of();
        }
        List<String> docIds = conversationIds.stream()
                .map(cid -> docId(ownerUserId, cid))
                .collect(Collectors.toList());
        Query query = Query.query(Criteria.where("_id").in(docIds));
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> findConversationIds(String ownerUserId) {
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId));
        query.fields().include("conversationId");
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(UserConversationDoc::getConversationId)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> findNotReceiveUserIds(String conversationId, List<String> candidateUserIds) {
        // 批量过滤低频操作，直接查 MongoDB
        List<String> ids = candidateUserIds.stream()
                .map(uid -> docId(uid, conversationId)).collect(Collectors.toList());
        Query query = Query.query(Criteria.where("_id").in(ids).and("recvMsgOpt").is(1));
        query.fields().include("ownerUserId");
        return mongoTemplate.find(query, UserConversationDoc.class).stream()
                .map(UserConversationDoc::getOwnerUserId).collect(Collectors.toList());
    }

    // ── toDomain 转换 ─────────────────────────────────────────────────────────

    private UserConversation toDomain(UserConversationDoc doc) {
        UserConversation state = new UserConversation();
        state.setOwnerUserId(doc.getOwnerUserId());
        state.setConversationId(doc.getConversationId());
        state.setConversationType(doc.getConversationType());
        state.setTargetId(doc.getTargetId());
        state.setRecvMsgOpt(doc.getRecvMsgOpt());
        state.setUnreadCount(doc.getUnreadCount());
        state.setLatestMsgSeq(doc.getLatestMsgSeq());
        state.setLatestMsg(doc.getLatestMsg());
        state.setPinned(doc.isPinned());
        state.setDraftText(doc.getDraftText());
        state.setAttachedInfo(doc.getAttachedInfo());
        state.setGroupAtType(doc.getGroupAtType());
        state.setPrivateChat(doc.isPrivateChat());
        state.setBurnDuration(doc.getBurnDuration());
        state.setMsgDestruct(doc.isMsgDestruct());
        state.setMsgDestructTime(doc.getMsgDestructTime());
        state.setLatestMsgDestructTime(doc.getLatestMsgDestructTime());
        state.setCreatedAt(doc.getCreatedAt() != null ? doc.getCreatedAt().toEpochMilli() : 0L);
        state.setUpdatedAt(doc.getUpdatedAt() != null ? doc.getUpdatedAt().toEpochMilli() : 0L);
        return state;
    }

    private static String docId(String ownerUserId, String conversationId) {
        return ownerUserId + ":" + conversationId;
    }
}
