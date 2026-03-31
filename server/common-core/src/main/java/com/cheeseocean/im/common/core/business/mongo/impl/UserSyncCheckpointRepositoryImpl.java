package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.domain.UserSyncCheckpoint;
import com.cheeseocean.im.common.core.business.mongo.document.UserSyncCheckpointDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.UserSyncCheckpointMongoRepository;
import com.cheeseocean.im.common.core.business.repository.UserSyncCheckpointRepository;
import com.cheeseocean.im.common.core.constants.RedisKeys;
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

public class UserSyncCheckpointRepositoryImpl implements UserSyncCheckpointRepository {

    private final UserSyncCheckpointMongoRepository mongoRepository;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public UserSyncCheckpointRepositoryImpl(UserSyncCheckpointMongoRepository mongoRepository,
                                            MongoTemplate mongoTemplate,
                                            StringRedisTemplate stringRedisTemplate) {
        this.mongoRepository = mongoRepository;
        this.mongoTemplate = mongoTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void createIfAbsent(String userId, String conversationId) {
        String id = docId(userId, conversationId);
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id", id)
                .setOnInsert("userId", userId)
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("readSeq", 0L)
                .setOnInsert("maxSeq", 0L)
                .setOnInsert("minSeq", 0L);
        mongoTemplate.upsert(query, update, UserSyncCheckpointDoc.class);
        stringRedisTemplate.opsForValue().setIfAbsent(RedisKeys.userSyncCheckpointReadSeq(userId, conversationId), "0");
        stringRedisTemplate.opsForValue().setIfAbsent(RedisKeys.userSyncCheckpointMaxSeq(userId, conversationId), "0");
        stringRedisTemplate.opsForValue().setIfAbsent(RedisKeys.userSyncCheckpointMinSeq(userId, conversationId), "0");
    }

    @Override
    public void updateReadSeq(String userId, String conversationId, long readSeq) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userSyncCheckpointReadSeq(userId, conversationId), String.valueOf(readSeq));
        Query query = Query.query(Criteria.where("_id").is(docId(userId, conversationId)));
        mongoTemplate.updateFirst(query, new Update().set("readSeq", readSeq), UserSyncCheckpointDoc.class);
    }

    @Override
    public void updateMaxSeq(String userId, String conversationId, long maxSeq) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userSyncCheckpointMaxSeq(userId, conversationId), String.valueOf(maxSeq));
        String id = docId(userId, conversationId);
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id", id)
                .setOnInsert("userId", userId)
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("readSeq", 0L)
                .setOnInsert("minSeq", 0L)
                .set("maxSeq", maxSeq);
        mongoTemplate.upsert(query, update, UserSyncCheckpointDoc.class);
    }

    @Override
    public void updateMinSeq(String userId, String conversationId, long minSeq) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userSyncCheckpointMinSeq(userId, conversationId), String.valueOf(minSeq));
        Query query = Query.query(Criteria.where("_id").is(docId(userId, conversationId)));
        mongoTemplate.updateFirst(query, new Update().set("minSeq", minSeq), UserSyncCheckpointDoc.class);
    }

    @Override
    public Optional<UserSyncCheckpoint> find(String userId, String conversationId) {
        List<String> vals = stringRedisTemplate.opsForValue().multiGet(List.of(
                RedisKeys.userSyncCheckpointReadSeq(userId, conversationId),
                RedisKeys.userSyncCheckpointMaxSeq(userId, conversationId),
                RedisKeys.userSyncCheckpointMinSeq(userId, conversationId)
        ));
        if (vals != null && vals.stream().allMatch(Objects::nonNull)) {
            UserSyncCheckpoint checkpoint = new UserSyncCheckpoint();
            checkpoint.setId(docId(userId, conversationId));
            checkpoint.setUserId(userId);
            checkpoint.setConversationId(conversationId);
            checkpoint.setReadSeq(Long.parseLong(vals.get(0)));
            checkpoint.setMaxSeq(Long.parseLong(vals.get(1)));
            checkpoint.setMinSeq(Long.parseLong(vals.get(2)));
            return Optional.of(checkpoint);
        }
        return mongoRepository.findByUserIdAndConversationId(userId, conversationId)
                .map(doc -> {
                    UserSyncCheckpoint checkpoint = toDomain(doc);
                    writeToCache(checkpoint);
                    return checkpoint;
                });
    }

    @Override
    public List<UserSyncCheckpoint> findByIds(String userId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return List.of();
        }

        List<String> dedupedIds = conversationIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
        if (dedupedIds.isEmpty()) {
            return List.of();
        }

        List<String> keys = new ArrayList<>(dedupedIds.size() * 3);
        for (String conversationId : dedupedIds) {
            keys.add(RedisKeys.userSyncCheckpointReadSeq(userId, conversationId));
            keys.add(RedisKeys.userSyncCheckpointMaxSeq(userId, conversationId));
            keys.add(RedisKeys.userSyncCheckpointMinSeq(userId, conversationId));
        }

        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        Map<String, UserSyncCheckpoint> checkpoints = new LinkedHashMap<>();
        List<String> misses = new ArrayList<>();

        for (int i = 0; i < dedupedIds.size(); i++) {
            int base = i * 3;
            String read = values == null ? null : values.get(base);
            String max = values == null ? null : values.get(base + 1);
            String min = values == null ? null : values.get(base + 2);
            String conversationId = dedupedIds.get(i);
            if (read != null && max != null && min != null) {
                UserSyncCheckpoint checkpoint = new UserSyncCheckpoint();
                checkpoint.setId(docId(userId, conversationId));
                checkpoint.setUserId(userId);
                checkpoint.setConversationId(conversationId);
                checkpoint.setReadSeq(Long.parseLong(read));
                checkpoint.setMaxSeq(Long.parseLong(max));
                checkpoint.setMinSeq(Long.parseLong(min));
                checkpoints.put(conversationId, checkpoint);
            } else {
                misses.add(conversationId);
            }
        }

        if (!misses.isEmpty()) {
            List<UserSyncCheckpoint> loaded = mongoRepository
                    .findByUserIdAndConversationIdIn(userId, misses)
                    .stream()
                    .map(this::toDomain)
                    .toList();
            for (UserSyncCheckpoint checkpoint : loaded) {
                writeToCache(checkpoint);
                checkpoints.put(checkpoint.getConversationId(), checkpoint);
            }
        }

        return dedupedIds.stream()
                .map(checkpoints::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<UserSyncCheckpoint> findByUserId(String userId) {
        List<UserSyncCheckpoint> results = mongoRepository.findByUserId(userId)
                .stream()
                .map(this::toDomain)
                .toList();
        results.forEach(this::writeToCache);
        return results;
    }

    private void writeToCache(UserSyncCheckpoint checkpoint) {
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userSyncCheckpointReadSeq(checkpoint.getUserId(), checkpoint.getConversationId()),
                String.valueOf(checkpoint.getReadSeq()));
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userSyncCheckpointMaxSeq(checkpoint.getUserId(), checkpoint.getConversationId()),
                String.valueOf(checkpoint.getMaxSeq()));
        stringRedisTemplate.opsForValue().set(
                RedisKeys.userSyncCheckpointMinSeq(checkpoint.getUserId(), checkpoint.getConversationId()),
                String.valueOf(checkpoint.getMinSeq()));
    }

    private UserSyncCheckpoint toDomain(UserSyncCheckpointDoc doc) {
        UserSyncCheckpoint checkpoint = new UserSyncCheckpoint();
        checkpoint.setId(doc.getId());
        checkpoint.setUserId(doc.getUserId());
        checkpoint.setConversationId(doc.getConversationId());
        checkpoint.setReadSeq(doc.getReadSeq());
        checkpoint.setMaxSeq(doc.getMaxSeq());
        checkpoint.setMinSeq(doc.getMinSeq());
        return checkpoint;
    }

    private static String docId(String userId, String conversationId) {
        return userId + ":" + conversationId;
    }
}
