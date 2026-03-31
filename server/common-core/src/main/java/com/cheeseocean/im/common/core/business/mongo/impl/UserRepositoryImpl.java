package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.domain.User;
import com.cheeseocean.im.common.core.business.mongo.document.user.UserDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.UserMongoRepository;
import com.cheeseocean.im.common.core.business.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link UserRepository} 的 MongoDB 实现。
 */
public class UserRepositoryImpl implements UserRepository {

    private static final int NOTIFICATION_ACCOUNT_MIN_LEVEL = 2;

    private final UserMongoRepository mongoRepository;
    private final MongoTemplate mongoTemplate;

    public UserRepositoryImpl(UserMongoRepository mongoRepository,
                               MongoTemplate mongoTemplate) {
        this.mongoRepository = mongoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // ── 读操作 ────────────────────────────────────────────────────────────────

    @Override
    public Optional<User> findById(String userId) {
        return mongoRepository.findById(userId).map(this::toDomain);
    }

    @Override
    public List<User> findByIds(List<String> userIds) {
        return mongoRepository.findAllById(userIds).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
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

    // ── 写操作 ────────────────────────────────────────────────────────────────

    @Override
    public void save(User user) {
        mongoRepository.save(toDoc(user));
    }

    @Override
    public void saveAll(List<User> users) {
        mongoRepository.saveAll(users.stream().map(this::toDoc).collect(Collectors.toList()));
    }

    @Override
    public void updateFields(String userId, Map<String, Object> fields) {
        Query query = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update();
        fields.forEach(update::set);
        mongoTemplate.updateFirst(query, update, UserDoc.class);
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
