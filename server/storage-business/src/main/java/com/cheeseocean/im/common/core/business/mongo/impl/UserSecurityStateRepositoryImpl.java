package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.session.UserSecurityState;
import com.cheeseocean.im.common.core.business.mongo.document.user.UserSecurityStateDoc;
import com.cheeseocean.im.common.core.business.repository.UserSecurityStateRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * {@link UserSecurityStateRepository} 的 MongoDB 实现。
 */
public class UserSecurityStateRepositoryImpl implements UserSecurityStateRepository {

    private static final long INITIAL_TOKEN_VERSION = 1L;

    private final MongoTemplate mongoTemplate;

    public UserSecurityStateRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<UserSecurityState> findByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        Query query = Query.query(Criteria.where("_id").is(userId));
        return Optional.ofNullable(mongoTemplate.findOne(query, UserSecurityStateDoc.class)).map(this::toDomain);
    }

    @Override
    public UserSecurityState bumpTokenVersion(String userId) {
        ensureExists(userId);
        long now = System.currentTimeMillis();
        Query query = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update()
                .inc("tokenVersion", 1L)
                .set("updatedAt", now);
        return toDomain(mongoTemplate.findAndModify(query, update, options(), UserSecurityStateDoc.class));
    }

    @Override
    public UserSecurityState setBanned(String userId, boolean banned) {
        ensureExists(userId);
        long now = System.currentTimeMillis();
        Query query = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update()
                .set("banned", banned)
                .inc("tokenVersion", 1L)
                .set("updatedAt", now);
        return toDomain(mongoTemplate.findAndModify(query, update, options(), UserSecurityStateDoc.class));
    }

    private void ensureExists(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId required");
        }
        long now = System.currentTimeMillis();
        Query query = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update()
                .setOnInsert("userId", userId)
                .setOnInsert("tokenVersion", INITIAL_TOKEN_VERSION)
                .setOnInsert("banned", false)
                .setOnInsert("updatedAt", now);
        mongoTemplate.upsert(query, update, UserSecurityStateDoc.class);
    }

    private FindAndModifyOptions options() {
        return FindAndModifyOptions.options().returnNew(true);
    }

    private UserSecurityState toDomain(UserSecurityStateDoc doc) {
        if (doc == null) {
            return defaultState(null);
        }
        UserSecurityState state = new UserSecurityState();
        state.setUserId(doc.getUserId());
        state.setTokenVersion(doc.getTokenVersion() <= 0 ? INITIAL_TOKEN_VERSION : doc.getTokenVersion());
        state.setBanned(doc.isBanned());
        state.setUpdatedAt(doc.getUpdatedAt());
        return state;
    }

    private UserSecurityState defaultState(String userId) {
        UserSecurityState state = new UserSecurityState();
        state.setUserId(userId);
        state.setTokenVersion(INITIAL_TOKEN_VERSION);
        state.setBanned(false);
        state.setUpdatedAt(0L);
        return state;
    }
}
