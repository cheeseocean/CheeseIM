package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.business.domain.UserConversationSyncPoint;
import com.cheeseocean.im.common.core.business.mongo.document.conversation.UserConversationSyncPointDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.UserConversationSyncPointMongoRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link UserConversationSyncPointRepository} 的 MongoDB + Redis 实现。
 *
 * <p><b>缓存策略：</b>
 * <ul>
 *   <li>readSeq：{@code uc:read:{userId}:{convId}} — 直写，读时取缺任意一项则全量回源 MongoDB。</li>
 *   <li>maxSeq：{@code uc:max:{userId}:{convId}} — 同上。</li>
 *   <li>minSeq：{@code uc:min:{userId}:{convId}} — 同上。</li>
 * </ul>
 *
 * <p>三个 key 均以 {@link StringRedisTemplate} 存储纯数字字符串，与
 * {@code RedisConversationStateStore} 保持一致，不设固定 TTL（由业务
 * 生命周期驱动失效）。
 *
 * <p>所有写操作以确定性 _id "{userId}:{conversationId}" upsert，保证幂等。
 */
public class UserConversationSyncPointRepositoryImpl implements UserConversationSyncPointRepository {

    private final UserConversationSyncPointMongoRepository mongoRepository;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public UserConversationSyncPointRepositoryImpl(
            UserConversationSyncPointMongoRepository mongoRepository,
            MongoTemplate mongoTemplate,
            StringRedisTemplate stringRedisTemplate) {
        this.mongoRepository = mongoRepository;
        this.mongoTemplate = mongoTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
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
        // 仅在 key 不存在时初始化缓存，避免覆盖已有值
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeys.userReadSeq(userId, conversationId), "0");
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeys.userMaxSeq(userId, conversationId), "0");
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeys.userMinSeq(userId, conversationId), "0");
    }

    @Override
    public void updateReadSeq(String userId, String conversationId, long readSeq) {
        // Redis 直写（热路径）
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userReadSeq(userId, conversationId), String.valueOf(readSeq));
        Query query = Query.query(Criteria.where("_id").is(docId(userId, conversationId)));
        mongoTemplate.updateFirst(query, new Update().set("readSeq", readSeq),
                UserConversationSyncPointDoc.class);
    }

    @Override
    public void updateMaxSeq(String userId, String conversationId, long maxSeq) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userMaxSeq(userId, conversationId), String.valueOf(maxSeq));
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
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userMinSeq(userId, conversationId), String.valueOf(minSeq));
        Query query = Query.query(Criteria.where("_id").is(docId(userId, conversationId)));
        mongoTemplate.updateFirst(query, new Update().set("minSeq", minSeq),
                UserConversationSyncPointDoc.class);
    }

    // ── 读操作 ────────────────────────────────────────────────────────────────

    @Override
    public Optional<UserConversationSyncPoint> find(String userId, String conversationId) {
        // 三个 key 全部命中才返回缓存，任意缺失均回源 MongoDB
        List<String> vals = stringRedisTemplate.opsForValue().multiGet(List.of(
                RedisKeys.userReadSeq(userId, conversationId),
                RedisKeys.userMaxSeq(userId, conversationId),
                RedisKeys.userMinSeq(userId, conversationId)
        ));
        if (vals != null && vals.stream().allMatch(Objects::nonNull)) {
            UserConversationSyncPoint range = new UserConversationSyncPoint();
            range.setUserId(userId);
            range.setConversationId(conversationId);
            range.setReadSeq(Long.parseLong(vals.get(0)));
            range.setMaxSeq(Long.parseLong(vals.get(1)));
            range.setMinSeq(Long.parseLong(vals.get(2)));
            return Optional.of(range);
        }
        // 缓存未命中：查 MongoDB 并写回
        return mongoRepository.findByUserIdAndConversationId(userId, conversationId)
                .map(doc -> {
                    UserConversationSyncPoint range = toDomain(doc);
                    writeToCache(userId, conversationId, range);
                    return range;
                });
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

        List<String> keys = new ArrayList<>(dedupedIds.size() * 3);
        for (String conversationId : dedupedIds) {
            keys.add(RedisKeys.userReadSeq(userId, conversationId));
            keys.add(RedisKeys.userMaxSeq(userId, conversationId));
            keys.add(RedisKeys.userMinSeq(userId, conversationId));
        }

        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        Map<String, UserConversationSyncPoint> ranges = new LinkedHashMap<>();
        List<String> misses = new ArrayList<>();

        for (int i = 0; i < dedupedIds.size(); i++) {
            int base = i * 3;
            String read = values == null ? null : values.get(base);
            String max = values == null ? null : values.get(base + 1);
            String min = values == null ? null : values.get(base + 2);
            String conversationId = dedupedIds.get(i);
            if (read != null && max != null && min != null) {
                UserConversationSyncPoint range = new UserConversationSyncPoint();
                range.setUserId(userId);
                range.setConversationId(conversationId);
                range.setReadSeq(Long.parseLong(read));
                range.setMaxSeq(Long.parseLong(max));
                range.setMinSeq(Long.parseLong(min));
                ranges.put(conversationId, range);
            } else {
                misses.add(conversationId);
            }
        }

        if (!misses.isEmpty()) {
            List<UserConversationSyncPoint> loaded = mongoRepository
                    .findByUserIdAndConversationIdIn(userId, misses)
                    .stream()
                    .map(this::toDomain)
                    .toList();
            for (UserConversationSyncPoint range : loaded) {
                writeToCache(userId, range.getConversationId(), range);
                ranges.put(range.getConversationId(), range);
            }
        }

        return dedupedIds.stream()
                .map(ranges::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<UserConversationSyncPoint> findByUserId(String userId) {
        List<UserConversationSyncPoint> results = mongoRepository.findByUserId(userId)
                .stream().map(this::toDomain).collect(Collectors.toList());
        // 顺带刷新各条目缓存
        results.forEach(r -> writeToCache(r.getUserId(), r.getConversationId(), r));
        return results;
    }

    // ── 缓存工具方法 ──────────────────────────────────────────────────────────

    private void writeToCache(String ownerUserId, String conversationId,
                              UserConversationSyncPoint range) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userReadSeq(ownerUserId, conversationId),
                String.valueOf(range.getReadSeq()));
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userMaxSeq(ownerUserId, conversationId),
                String.valueOf(range.getMaxSeq()));
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userMinSeq(ownerUserId, conversationId),
                String.valueOf(range.getMinSeq()));
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
