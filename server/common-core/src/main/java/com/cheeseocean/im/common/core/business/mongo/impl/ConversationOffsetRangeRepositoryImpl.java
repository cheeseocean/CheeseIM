package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.business.domain.ConversationOffsetRange;
import com.cheeseocean.im.common.core.business.mongo.document.ConversationOffsetRangeDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.ConversationOffsetRangeMongoRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationOffsetRangeRepository;
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
 * {@link ConversationOffsetRangeRepository} 的 MongoDB + Redis 实现。
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
 * <p>所有写操作以确定性 _id "{ownerUserId}:{conversationId}" upsert，保证幂等。
 */
public class ConversationOffsetRangeRepositoryImpl implements ConversationOffsetRangeRepository {

    private final ConversationOffsetRangeMongoRepository mongoRepository;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public ConversationOffsetRangeRepositoryImpl(
            ConversationOffsetRangeMongoRepository mongoRepository,
            MongoTemplate mongoTemplate,
            StringRedisTemplate stringRedisTemplate) {
        this.mongoRepository = mongoRepository;
        this.mongoTemplate = mongoTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ── 写操作 ────────────────────────────────────────────────────────────────

    @Override
    public void createIfAbsent(String ownerUserId, String conversationId) {
        String id = docId(ownerUserId, conversationId);
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id",            id)
                .setOnInsert("ownerUserId",     ownerUserId)
                .setOnInsert("conversationId",  conversationId)
                .setOnInsert("maxSeq",          0L)
                .setOnInsert("minSeq",          0L)
                .setOnInsert("readSeq",         0L);
        mongoTemplate.upsert(query, update, ConversationOffsetRangeDoc.class);
        // 仅在 key 不存在时初始化缓存，避免覆盖已有值
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeys.userReadSeq(ownerUserId, conversationId), "0");
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeys.userMaxSeq(ownerUserId, conversationId), "0");
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeys.userMinSeq(ownerUserId, conversationId), "0");
    }

    @Override
    public void updateReadSeq(String ownerUserId, String conversationId, long readSeq) {
        // Redis 直写（热路径）
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userReadSeq(ownerUserId, conversationId), String.valueOf(readSeq));
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        mongoTemplate.updateFirst(query, new Update().set("readSeq", readSeq),
                ConversationOffsetRangeDoc.class);
    }

    @Override
    public void updateMaxSeq(String ownerUserId, String conversationId, long maxSeq) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userMaxSeq(ownerUserId, conversationId), String.valueOf(maxSeq));
        String id = docId(ownerUserId, conversationId);
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id",           id)
                .setOnInsert("ownerUserId",    ownerUserId)
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("minSeq",         0L)
                .setOnInsert("readSeq",        0L)
                .set("maxSeq", maxSeq);
        mongoTemplate.upsert(query, update, ConversationOffsetRangeDoc.class);
    }

    @Override
    public void updateMinSeq(String ownerUserId, String conversationId, long minSeq) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userMinSeq(ownerUserId, conversationId), String.valueOf(minSeq));
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, conversationId)));
        mongoTemplate.updateFirst(query, new Update().set("minSeq", minSeq),
                ConversationOffsetRangeDoc.class);
    }

    // ── 读操作 ────────────────────────────────────────────────────────────────

    @Override
    public Optional<ConversationOffsetRange> find(String ownerUserId, String conversationId) {
        // 三个 key 全部命中才返回缓存，任意缺失均回源 MongoDB
        List<String> vals = stringRedisTemplate.opsForValue().multiGet(List.of(
                RedisKeys.userReadSeq(ownerUserId, conversationId),
                RedisKeys.userMaxSeq(ownerUserId, conversationId),
                RedisKeys.userMinSeq(ownerUserId, conversationId)
        ));
        if (vals != null && vals.stream().allMatch(Objects::nonNull)) {
            ConversationOffsetRange range = new ConversationOffsetRange();
            range.setOwnerUserId(ownerUserId);
            range.setConversationId(conversationId);
            range.setReadSeq(Long.parseLong(vals.get(0)));
            range.setMaxSeq(Long.parseLong(vals.get(1)));
            range.setMinSeq(Long.parseLong(vals.get(2)));
            return Optional.of(range);
        }
        // 缓存未命中：查 MongoDB 并写回
        return mongoRepository.findByOwnerUserIdAndConversationId(ownerUserId, conversationId)
                .map(doc -> {
                    ConversationOffsetRange range = toDomain(doc);
                    writeToCache(ownerUserId, conversationId, range);
                    return range;
                });
    }

    @Override
    public List<ConversationOffsetRange> findByIds(String ownerUserId, List<String> conversationIds) {
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
            keys.add(RedisKeys.userReadSeq(ownerUserId, conversationId));
            keys.add(RedisKeys.userMaxSeq(ownerUserId, conversationId));
            keys.add(RedisKeys.userMinSeq(ownerUserId, conversationId));
        }

        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        Map<String, ConversationOffsetRange> ranges = new LinkedHashMap<>();
        List<String> misses = new ArrayList<>();

        for (int i = 0; i < dedupedIds.size(); i++) {
            int base = i * 3;
            String read = values == null ? null : values.get(base);
            String max = values == null ? null : values.get(base + 1);
            String min = values == null ? null : values.get(base + 2);
            String conversationId = dedupedIds.get(i);
            if (read != null && max != null && min != null) {
                ConversationOffsetRange range = new ConversationOffsetRange();
                range.setOwnerUserId(ownerUserId);
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
            List<ConversationOffsetRange> loaded = mongoRepository
                    .findByOwnerUserIdAndConversationIdIn(ownerUserId, misses)
                    .stream()
                    .map(this::toDomain)
                    .toList();
            for (ConversationOffsetRange range : loaded) {
                writeToCache(ownerUserId, range.getConversationId(), range);
                ranges.put(range.getConversationId(), range);
            }
        }

        return dedupedIds.stream()
                .map(ranges::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<ConversationOffsetRange> findByOwner(String ownerUserId) {
        List<ConversationOffsetRange> results = mongoRepository.findByOwnerUserId(ownerUserId)
                .stream().map(this::toDomain).collect(Collectors.toList());
        // 顺带刷新各条目缓存
        results.forEach(r -> writeToCache(r.getOwnerUserId(), r.getConversationId(), r));
        return results;
    }

    // ── 缓存工具方法 ──────────────────────────────────────────────────────────

    private void writeToCache(String ownerUserId, String conversationId,
                              ConversationOffsetRange range) {
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

    private ConversationOffsetRange toDomain(ConversationOffsetRangeDoc doc) {
        ConversationOffsetRange range = new ConversationOffsetRange();
        range.setOwnerUserId(doc.getOwnerUserId());
        range.setConversationId(doc.getConversationId());
        range.setMaxSeq(doc.getMaxSeq());
        range.setMinSeq(doc.getMinSeq());
        range.setReadSeq(doc.getReadSeq());
        return range;
    }

    private static String docId(String ownerUserId, String conversationId) {
        return ownerUserId + ":" + conversationId;
    }
}
