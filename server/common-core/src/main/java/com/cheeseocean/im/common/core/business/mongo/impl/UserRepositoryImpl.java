package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.User;
import com.cheeseocean.im.common.core.business.mongo.document.user.UserDoc;
import com.cheeseocean.im.common.core.business.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link UserRepository} 的 MongoDB 实现。
 */
public class UserRepositoryImpl implements UserRepository {

    private static final int NOTIFICATION_ACCOUNT_MIN_LEVEL = 2;

    private final MongoTemplate mongoTemplate;

    public UserRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<User> findById(String userId) {
        Query query = Query.query(Criteria.where("_id").is(userId));
        return Optional.ofNullable(mongoTemplate.findOne(query, UserDoc.class)).map(this::toDomain);
    }

    @Override
    public List<User> findByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("_id").in(userIds));
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void saveAll(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        // 用户写入统一走批量 upsert，兼容初始化导入和后续幂等同步。
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UserDoc.class);
        int operations = 0;
        for (User user : users) {
            if (user == null || !StringUtils.hasText(user.getUserId())) {
                continue;
            }
            bulkOperations.upsert(
                    Query.query(Criteria.where("_id").is(user.getUserId())),
                    toUpdate(user)
            );
            operations++;
        }
        if (operations > 0) {
            bulkOperations.execute();
        }
    }

    @Override
    public void updateFields(String userId, Map<String, Object> fields) {
        if (!StringUtils.hasText(userId) || fields == null || fields.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update();
        fields.forEach((key, value) -> {
            if (key != null) {
                update.set(key, value);
            }
        });
        mongoTemplate.updateFirst(query, update, UserDoc.class);
    }

    @Override
    public boolean exists(String userId) {
        Query query = Query.query(Criteria.where("_id").is(userId));
        return mongoTemplate.exists(query, UserDoc.class);
    }

    @Override
    public List<User> findByNickname(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("nickname").regex(nickname, "i"));
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<User> findByAppManagerLevelGte(int level) {
        Query query = Query.query(Criteria.where("appManagerLevel").gte(level));
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<User> pageAll(int limit, int offset) {
        // 普通用户列表默认排除通知/系统账号。
        Query query = Query.query(Criteria.where("appManagerLevel").lt(NOTIFICATION_ACCOUNT_MIN_LEVEL))
                .with(PageRequest.of(Math.max(0, offset) / Math.max(1, limit == 0 ? 1 : limit), Math.max(1, limit), Sort.by("createTime").descending()));
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countAll() {
        return mongoTemplate.count(Query.query(Criteria.where("appManagerLevel").lt(NOTIFICATION_ACCOUNT_MIN_LEVEL)), UserDoc.class);
    }

    @Override
    public List<User> pageByKeyword(String keyword, int limit, int offset) {
        Query query = buildUserQuery(keyword)
                .with(PageRequest.of(Math.max(0, offset) / Math.max(1, limit == 0 ? 1 : limit), Math.max(1, limit), Sort.by("createTime").descending()));
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByKeyword(String keyword) {
        return mongoTemplate.count(buildUserQuery(keyword), UserDoc.class);
    }

    @Override
    public List<String> findAllUserIds(int limit, int offset) {
        Query query = new Query()
                .with(PageRequest.of(Math.max(0, offset) / Math.max(1, limit == 0 ? 1 : limit), Math.max(1, limit)))
                .with(Sort.by("createTime").ascending());
        query.fields().include("userId");
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(UserDoc::getUserId)
                .toList();
    }

    @Override
    public List<String> findExistingUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("_id").in(userIds));
        query.fields().include("userId");
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(UserDoc::getUserId)
                .toList();
    }

    @Override
    public List<User> pageNotificationAccounts(String keyword, Integer appManagerLevel, int limit, int offset) {
        // 通知账号查询与普通用户分页分流，避免搜索条件互相污染。
        Criteria criteria = Criteria.where("appManagerLevel").gte(
                appManagerLevel != null ? appManagerLevel : NOTIFICATION_ACCOUNT_MIN_LEVEL
        );
        if (StringUtils.hasText(keyword)) {
            criteria = new Criteria().andOperator(
                    criteria,
                    new Criteria().orOperator(
                            Criteria.where("_id").is(keyword),
                            Criteria.where("nickname").regex(keyword, "i")
                    )
            );
        }
        Query query = Query.query(criteria)
                .with(PageRequest.of(Math.max(0, offset) / Math.max(1, limit == 0 ? 1 : limit), Math.max(1, limit)));
        return mongoTemplate.find(query, UserDoc.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int getGlobalReceiveOption(String userId) {
        // 缺失用户按正常接收处理，和现有业务默认值保持一致。
        return findById(userId).map(User::getReceiveOpt).orElse(0);
    }

    private User toDomain(UserDoc doc) {
        User user = new User();
        user.setUserId(doc.getUserId());
        user.setNickname(doc.getNickname());
        user.setAvatarUrl(doc.getAvatarUrl());
        user.setEx(doc.getEx());
        user.setAppManagerLevel(doc.getAppManagerLevel());
        user.setReceiveOpt(doc.getReceiveOpt());
        user.setCreateTime(doc.getCreateTime());
        return user;
    }

    private Update toUpdate(User user) {
        Update update = new Update()
                .set("userId", user.getUserId())
                .set("nickname", user.getNickname())
                .set("avatarUrl", user.getAvatarUrl())
                .set("ex", user.getEx())
                .set("appManagerLevel", user.getAppManagerLevel())
                .set("receiveOpt", user.getReceiveOpt());
        if (user.getCreateTime() > 0) {
            update.setOnInsert("createTime", user.getCreateTime());
        }
        return update;
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
