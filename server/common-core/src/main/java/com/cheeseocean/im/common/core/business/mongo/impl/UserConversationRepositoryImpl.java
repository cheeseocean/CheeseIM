package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.cache.redis.BatchCacheHelper;
import com.cheeseocean.im.common.core.cache.redis.StringSetCacheHelper;
import com.cheeseocean.im.common.core.business.domain.UserConversation;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.UserConversationDoc;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link UserConversationRepository} 的 MongoDB + Redis 实现。
 *
 * <p><b>缓存策略：</b>
 * <ul>
 *   <li>会话业务状态：{@code cheese_im:conv_state:{userId}:{convId}} 缓存完整对象，TTL 12h，写时失效。</li>
 *   <li>会话 ID 集合：{@code cheese_im:conv_ids:{userId}} 缓存为 Redis SET，createIfAbsent 时追加。</li>
 *   <li>未读计数：{@code uc:unread:{userId}:{convId}} 缓存为字符串计数器，INCR/SET 直写，与 MongoDB 保持最终一致。</li>
 * </ul>
 *
 * <p>所有写操作以确定性 _id "{ownerUserId}:{conversationId}" upsert，保证幂等可重试。
 */
public class UserConversationRepositoryImpl implements UserConversationRepository {

    /** 会话业务状态缓存 TTL */
    private static final Duration CONV_STATE_TTL = Duration.ofHours(12);

    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public UserConversationRepositoryImpl(MongoTemplate mongoTemplate,
                                               RedisTemplate<String, Object> redisTemplate,
                                               StringRedisTemplate stringRedisTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
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
        // 将会话 ID 加入用户会话集合缓存
        redisTemplate.opsForSet().add(
                RedisKeys.userConvIds(state.getOwnerUserId()), state.getConversationId());
        StringSetCacheHelper.markLoaded(redisTemplate, RedisKeys.userConvIdsLoaded(state.getOwnerUserId()));
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
        // 会话状态已变更，使缓存失效
        redisTemplate.delete(RedisKeys.userConvState(ownerUserId, conversationId));
    }

    @Override
    public void incrementUnread(String ownerUserId, String conversationId, int delta) {
        // Redis 计数器直写（热路径），MongoDB 作为持久化备份
        stringRedisTemplate.opsForValue().increment(
                RedisKeys.userUnread(ownerUserId, conversationId), delta);
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        Update update = new Update()
                .inc("unreadCount", delta)
                .set("updatedAt",   Instant.now());
        mongoTemplate.upsert(query, update, UserConversationDoc.class);
        redisTemplate.delete(RedisKeys.userConvState(ownerUserId, conversationId));
    }

    @Override
    public void clearUnread(String ownerUserId, String conversationId) {
        // 重置 Redis 计数器
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userUnread(ownerUserId, conversationId), "0");
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        Update update = new Update()
                .set("unreadCount", 0)
                .set("updatedAt",   Instant.now());
        mongoTemplate.updateFirst(query, update, UserConversationDoc.class);
        redisTemplate.delete(RedisKeys.userConvState(ownerUserId, conversationId));
    }

    @Override
    public void setRecvMsgOpt(String ownerUserId, String conversationId, int recvMsgOpt) {
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        Update update = new Update()
                .set("recvMsgOpt", recvMsgOpt)
                .set("updatedAt",  Instant.now());
        mongoTemplate.upsert(query, update, UserConversationDoc.class);
        redisTemplate.delete(RedisKeys.userConvState(ownerUserId, conversationId));
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
        redisTemplate.delete(RedisKeys.userConvState(ownerUserId, conversationId));
        redisTemplate.opsForSet().add(RedisKeys.userConvIds(ownerUserId), conversationId);
        StringSetCacheHelper.markLoaded(redisTemplate, RedisKeys.userConvIdsLoaded(ownerUserId));
    }

    // ── 读操作 ────────────────────────────────────────────────────────────────

    @Override
    public int getRecvMsgOpt(String ownerUserId, String conversationId) {
        // 优先从缓存会话状态中读取
        UserConversation cached = getConvStateFromCache(ownerUserId, conversationId);
        if (cached != null) {
            return cached.getRecvMsgOpt();
        }
        // 缓存未命中：只查 recvMsgOpt 字段，避免加载整个文档
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        query.fields().include("recvMsgOpt");
        UserConversationDoc doc = mongoTemplate.findOne(query, UserConversationDoc.class);
        return doc == null ? 0 : doc.getRecvMsgOpt();
    }

    @Override
    public UserConversation findOne(String ownerUserId, String conversationId) {
        return BatchCacheHelper.getCache(
                redisTemplate,
                RedisKeys.userConvState(ownerUserId, conversationId),
                CONV_STATE_TTL,
                () -> {
                    Query               query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
                    UserConversationDoc doc   = mongoTemplate.findOne(query, UserConversationDoc.class);
                    return doc == null ? java.util.Optional.empty() : java.util.Optional.of(toDomain(doc));
                },
                UserConversation.class
        ).orElse(null);
    }

    @Override
    public List<UserConversation> findAll(String ownerUserId) {
        // MongoDB 负责排序，同时将结果写入各自的缓存 key
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId))
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<UserConversation> results = mongoTemplate.find(query, UserConversationDoc.class)
                .stream().map(this::toDomain).collect(Collectors.toList());
        // 顺带刷新各条目的缓存
        results.forEach(s -> putConvStateToCache(s.getOwnerUserId(), s.getConversationId(), s));
        return results;
    }

    /**
     * batchGetCache2 实现：批量检查 Redis，未命中的从 MongoDB 一次批量查询，再写回 Redis。
     */
    @Override
    public List<UserConversation> findByIds(String ownerUserId, List<String> conversationIds) {
        return BatchCacheHelper.batchGetCache2(
                redisTemplate,
                CONV_STATE_TTL,
                conversationIds,
                id -> RedisKeys.userConvState(ownerUserId, id),
                UserConversation::getConversationId,
                ids -> {
                    List<String> docIds = ids.stream()
                            .map(cid -> docId(ownerUserId, cid))
                            .collect(Collectors.toList());
                    Query query = Query.query(Criteria.where("_id").in(docIds));
                    return mongoTemplate.find(query, UserConversationDoc.class).stream()
                            .map(this::toDomain)
                            .collect(Collectors.toList());
                },
                UserConversation.class
        );
    }

    @Override
    public List<String> findConversationIds(String ownerUserId) {
        return StringSetCacheHelper.getOrLoad(
                redisTemplate,
                RedisKeys.userConvIds(ownerUserId),
                RedisKeys.userConvIdsLoaded(ownerUserId),
                () -> {
                    Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId));
                    query.fields().include("conversationId");
                    return mongoTemplate.find(query, UserConversationDoc.class).stream()
                            .map(UserConversationDoc::getConversationId)
                            .collect(Collectors.toList());
                }
        );
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

    // ── 缓存工具方法 ──────────────────────────────────────────────────────────

    private UserConversation getConvStateFromCache(String ownerUserId, String conversationId) {
        Object val = redisTemplate.opsForValue().get(
                RedisKeys.userConvState(ownerUserId, conversationId));
        return val instanceof UserConversation state ? state : null;
    }

    private void putConvStateToCache(String ownerUserId, String conversationId,
                                     UserConversation state) {
        redisTemplate.opsForValue().set(
                RedisKeys.userConvState(ownerUserId, conversationId), state, CONV_STATE_TTL);
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
