package com.cheeseocean.im.business.infra.mongo.impl;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.cache.redis.BatchCacheHelper;
import com.cheeseocean.im.business.domain.User;
import com.cheeseocean.im.business.infra.mongo.document.UserDoc;
import com.cheeseocean.im.business.infra.mongo.repository.UserMongoRepository;
import com.cheeseocean.im.business.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link UserRepository} 的 MongoDB + Redis 实现。
 *
 * <p><b>缓存策略：</b>
 * <ul>
 *   <li>用户信息：{@code cheese_im:user_info:{userId}} 缓存完整 User 对象，TTL 12h，写时失效。</li>
 * </ul>
 *
 * <p>{@link #findByIds} 采用 batchGetCache2 模式：先批量读 Redis，未命中的
 * 一次性从 MongoDB 批量查询，再写回 Redis，最后按入参顺序返回结果。
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    /** 用户信息缓存 TTL */
    private static final Duration USER_TTL = Duration.ofHours(12);
    private static final int NOTIFICATION_ACCOUNT_MIN_LEVEL = 2;

    private final UserMongoRepository mongoRepository;
    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserRepositoryImpl(UserMongoRepository mongoRepository,
                               MongoTemplate mongoTemplate,
                               RedisTemplate<String, Object> redisTemplate) {
        this.mongoRepository = mongoRepository;
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
    }

    // ── 读操作 ────────────────────────────────────────────────────────────────

    @Override
    public Optional<User> findById(String userId) {
        return BatchCacheHelper.getCache(
                redisTemplate,
                RedisKeys.userInfo(userId),
                USER_TTL,
                () -> mongoRepository.findById(userId).map(this::toDomain),
                User.class
        );
    }

    /**
     * batchGetCache2：批量先查 Redis，未命中的从 MongoDB 一次批量补全，再写回缓存。
     */
    @Override
    public List<User> findByIds(List<String> userIds) {
        return BatchCacheHelper.batchGetCache2(
                redisTemplate,
                USER_TTL,
                userIds,
                RedisKeys::userInfo,
                User::getUserId,
                ids -> mongoRepository.findAllById(ids).stream()
                        .map(this::toDomain)
                        .collect(Collectors.toList()),
                User.class
        );
    }

    @Override
    public List<User> queryUsers(String keyword, int pageNum, int pageSize) {
        Query query = buildUserQuery(keyword);
        query.with(PageRequest.of(pageNum - 1, pageSize, Sort.by("createTime").descending()));
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countUsers(String keyword) {
        return mongoTemplate.count(buildUserQuery(keyword), UserDoc.class);
    }

    @Override
    public List<String> findAllUserIds(int pageNum, int pageSize) {
        Query query = new Query()
                .with(PageRequest.of(pageNum - 1, pageSize))
                .with(Sort.by("createTime").ascending());
        query.fields().include("userId");
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(UserDoc::getUserId).collect(Collectors.toList());
    }

    @Override
    public List<String> filterExistingIds(List<String> userIds) {
        return mongoRepository.findAllById(userIds).stream()
                .map(UserDoc::getUserId).collect(Collectors.toList());
    }

    @Override
    public boolean existsById(String userId) {
        return mongoRepository.existsById(userId);
    }

    @Override
    public List<User> queryNotificationAccounts(String keyword, Integer appManagerLevel,
                                                int pageNum, int pageSize) {
        Criteria criteria = Criteria.where("appManagerLevel").gte(NOTIFICATION_ACCOUNT_MIN_LEVEL);
        if (appManagerLevel != null) {
            criteria = Criteria.where("appManagerLevel").is(appManagerLevel);
        }
        if (StringUtils.hasText(keyword)) {
            criteria = new Criteria().andOperator(
                    criteria,
                    new Criteria().orOperator(
                            Criteria.where("_id").is(keyword),
                            Criteria.where("nickname").regex(keyword, "i")
                    )
            );
        }
        Query query = Query.query(criteria).with(PageRequest.of(pageNum - 1, pageSize));
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(this::toDomain).collect(Collectors.toList());
    }

    // ── 写操作（写后失效缓存）────────────────────────────────────────────────

    @Override
    public void save(User user) {
        mongoRepository.save(toDoc(user));
        redisTemplate.delete(RedisKeys.userInfo(user.getUserId()));
    }

    @Override
    public void saveAll(List<User> users) {
        mongoRepository.saveAll(users.stream().map(this::toDoc).collect(Collectors.toList()));
        List<String> keys = users.stream()
                .map(u -> RedisKeys.userInfo(u.getUserId()))
                .collect(Collectors.toList());
        redisTemplate.delete(keys);
    }

    @Override
    public void updateFields(String userId, Map<String, Object> fields) {
        Query query = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update();
        fields.forEach(update::set);
        mongoTemplate.updateFirst(query, update, UserDoc.class);
        redisTemplate.delete(RedisKeys.userInfo(userId));
    }

    // ── 转换方法 ─────────────────────────────────────────────────────────────

    private User toDomain(UserDoc doc) {
        User user = new User();
        user.setUserId(doc.getUserId());
        user.setNickname(doc.getNickname());
        user.setFaceUrl(doc.getFaceUrl());
        user.setEx(doc.getEx());
        user.setAppManagerLevel(doc.getAppManagerLevel());
        user.setGlobalRecvMsgOpt(doc.getGlobalRecvMsgOpt());
        user.setCreateTime(doc.getCreateTime() != null ? doc.getCreateTime().toEpochMilli() : 0L);
        return user;
    }

    private UserDoc toDoc(User user) {
        UserDoc doc = new UserDoc();
        doc.setUserId(user.getUserId());
        doc.setNickname(user.getNickname());
        doc.setFaceUrl(user.getFaceUrl());
        doc.setEx(user.getEx());
        doc.setAppManagerLevel(user.getAppManagerLevel());
        doc.setGlobalRecvMsgOpt(user.getGlobalRecvMsgOpt());
        doc.setCreateTime(user.getCreateTime() > 0 ? Instant.ofEpochMilli(user.getCreateTime()) : null);
        return doc;
    }

    private Query buildUserQuery(String keyword) {
        Criteria base = Criteria.where("appManagerLevel").lt(NOTIFICATION_ACCOUNT_MIN_LEVEL);
        if (!StringUtils.hasText(keyword)) {
            return Query.query(base);
        }
        Criteria search = new Criteria().orOperator(
                Criteria.where("_id").is(keyword),
                Criteria.where("nickname").regex(keyword, "i")
        );
        return Query.query(new Criteria().andOperator(base, search));
    }
}
