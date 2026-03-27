package com.cheeseocean.im.social.repository;

import com.cheeseocean.im.social.domain.ConversationDoc;
import com.cheeseocean.im.social.model.Conversation;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link ConversationStore} 的 MongoDB 实现。
 *
 * 所有写操作均以确定性 _id "{ownerUserId}:{conversationId}" 进行 upsert，保证幂等可重试。
 *
 * 操作映射：
 *   createIfAbsent      → $setOnInsert（仅插入结构字段）
 *   updateLatestMessage → $set latestMsgSeq / latestMsg / updatedAt
 *   incrementUnread     → $inc unreadCount
 *   clearUnread         → updateFirst $set unreadCount=0 / readSeq / updatedAt
 */
@Repository
public class MongoConversationStore implements ConversationStore {

    private final MongoTemplate mongoTemplate;

    public MongoConversationStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void createIfAbsent(Conversation conversation) {
        String id = docId(conversation.getOwnerUserId(), conversation.getConversationId());
        Query  query  = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id",              id)
                .setOnInsert("ownerUserId",       conversation.getOwnerUserId())
                .setOnInsert("conversationId",    conversation.getConversationId())
                .setOnInsert("conversationType",  conversation.getConversationType())
                .setOnInsert("targetId",          conversation.getTargetId())
                .setOnInsert("recvMsgOpt",        conversation.getRecvMsgOpt())
                .setOnInsert("unreadCount",       0)
                .setOnInsert("pinned",            false)
                .setOnInsert("createdAt",         Instant.now());
        mongoTemplate.upsert(query, update, ConversationDoc.class);
    }

    @Override
    public void updateLatestMessage(String ownerUserId, String conversationId,
                                    long latestMsgSeq, String latestMsgJson) {
        String id     = docId(ownerUserId, conversationId);
        Query  query  = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("latestMsgSeq", latestMsgSeq)
                .set("latestMsg",    latestMsgJson)
                .set("updatedAt",    Instant.now());
        mongoTemplate.upsert(query, update, ConversationDoc.class);
    }

    @Override
    public void incrementUnread(String ownerUserId, String conversationId, int delta) {
        String id     = docId(ownerUserId, conversationId);
        Query  query  = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .inc("unreadCount", delta)
                .set("updatedAt",   Instant.now());
        mongoTemplate.upsert(query, update, ConversationDoc.class);
    }

    @Override
    public void clearUnread(String ownerUserId, String conversationId, long readSeq) {
        String id     = docId(ownerUserId, conversationId);
        Query  query  = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("unreadCount", 0)
                .set("readSeq",     readSeq)
                .set("updatedAt",   Instant.now());
        mongoTemplate.updateFirst(query, update, ConversationDoc.class);
    }

    @Override
    public void setRecvMsgOpt(String ownerUserId, String conversationId, int recvMsgOpt) {
        String id     = docId(ownerUserId, conversationId);
        Query  query  = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .set("recvMsgOpt", recvMsgOpt)
                .set("updatedAt",  Instant.now());
        mongoTemplate.upsert(query, update, ConversationDoc.class);
    }

    @Override
    public int getRecvMsgOpt(String ownerUserId, String conversationId) {
        String id    = docId(ownerUserId, conversationId);
        Query  query = Query.query(Criteria.where("_id").is(id));
        query.fields().include("recvMsgOpt");
        ConversationDoc doc = mongoTemplate.findOne(query, ConversationDoc.class);
        return doc == null ? 0 : doc.getRecvMsgOpt();
    }

    @Override
    public Conversation findOne(String ownerUserId, String conversationId) {
        String id    = docId(ownerUserId, conversationId);
        Query  query = Query.query(Criteria.where("_id").is(id));
        ConversationDoc doc = mongoTemplate.findOne(query, ConversationDoc.class);
        return doc == null ? null : toModel(doc);
    }

    @Override
    public List<Conversation> findAll(String ownerUserId) {
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId))
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<ConversationDoc> docs = mongoTemplate.find(query, ConversationDoc.class);
        return docs.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<Conversation> findByIds(String ownerUserId, List<String> conversationIds) {
        List<String> ids = conversationIds.stream()
                .map(cid -> docId(ownerUserId, cid))
                .collect(Collectors.toList());
        Query query = Query.query(Criteria.where("_id").in(ids));
        List<ConversationDoc> docs = mongoTemplate.find(query, ConversationDoc.class);
        return docs.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<String> findConversationIds(String ownerUserId) {
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId));
        query.fields().include("conversationId");
        List<ConversationDoc> docs = mongoTemplate.find(query, ConversationDoc.class);
        return docs.stream().map(ConversationDoc::getConversationId).collect(Collectors.toList());
    }

    @Override
    public List<String> findNotReceiveUserIds(String conversationId, List<String> candidateUserIds) {
        List<String> ids = candidateUserIds.stream()
                .map(uid -> docId(uid, conversationId))
                .collect(Collectors.toList());
        // recvMsgOpt = NOT_RECEIVE(1) 的用户
        Query query = Query.query(
                Criteria.where("_id").in(ids).and("recvMsgOpt").is(1)
        );
        query.fields().include("ownerUserId");
        List<ConversationDoc> docs = mongoTemplate.find(query, ConversationDoc.class);
        return docs.stream().map(ConversationDoc::getOwnerUserId).collect(Collectors.toList());
    }

    @Override
    public void upsertFields(String ownerUserId, String conversationId,
                             int conversationType, String targetId,
                             Map<String, Object> fields) {
        String id     = docId(ownerUserId, conversationId);
        Query  query  = Query.query(Criteria.where("_id").is(id));
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
        mongoTemplate.upsert(query, update, ConversationDoc.class);
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────────

    private static String docId(String ownerUserId, String conversationId) {
        return ownerUserId + ":" + conversationId;
    }

    /** 将 MongoDB 文档映射为领域模型。 */
    private Conversation toModel(ConversationDoc doc) {
        Conversation conv = new Conversation();
        conv.setOwnerUserId(doc.getOwnerUserId());
        conv.setConversationId(doc.getConversationId());
        conv.setConversationType(doc.getConversationType());
        conv.setTargetId(doc.getTargetId());
        conv.setRecvMsgOpt(doc.getRecvMsgOpt());
        conv.setUnreadCount(doc.getUnreadCount());
        conv.setLatestMsgSeq(doc.getLatestMsgSeq());
        conv.setLatestMsg(doc.getLatestMsg());
        conv.setPinned(doc.isPinned());
        conv.setDraftText(doc.getDraftText());
        conv.setAttachedInfo(doc.getAttachedInfo());
        return conv;
    }
}
