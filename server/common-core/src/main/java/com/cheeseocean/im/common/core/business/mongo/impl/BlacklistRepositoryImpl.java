package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.Blacklist;
import com.cheeseocean.im.common.core.business.mongo.document.user.BlacklistDoc;
import com.cheeseocean.im.common.core.business.repository.BlacklistRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link BlacklistRepository} 的 MongoDB 实现。
 */
public class BlacklistRepositoryImpl implements BlacklistRepository {

    private final MongoTemplate mongoTemplate;

    public BlacklistRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean isBlocked(String userId, String targetUserId) {
        // 这里查的是“targetUserId 是否把 userId 拉黑”，对应单聊权限判断方向。
        Query query = Query.query(Criteria.where("_id").is(docId(targetUserId, userId)));
        return mongoTemplate.exists(query, BlacklistDoc.class);
    }

    @Override
    public void blockUser(String userId, String targetUserId) {
        if (isBlank(userId) || isBlank(targetUserId)) {
            return;
        }
        // 拉黑是幂等写，已存在就直接返回。
        Query query = Query.query(Criteria.where("_id").is(docId(userId, targetUserId)));
        if (mongoTemplate.exists(query, BlacklistDoc.class)) {
            return;
        }
        BlacklistDoc doc = new BlacklistDoc();
        doc.setId(docId(userId, targetUserId));
        doc.setOwnerUserId(userId);
        doc.setBlockUserId(targetUserId);
        doc.setCreatedAt(System.currentTimeMillis());
        mongoTemplate.save(doc);
    }

    @Override
    public void unblockUser(String userId, String targetUserId) {
        Query query = Query.query(Criteria.where("_id").is(docId(userId, targetUserId)));
        mongoTemplate.remove(query, BlacklistDoc.class);
    }

    @Override
    public List<String> listBlockedUserIds(String userId) {
        // 列表接口只需要被拉黑用户 ID，不需要把完整黑名单文档全抬上去。
        Query query = Query.query(Criteria.where("ownerUserId").is(userId));
        query.fields().include("blockUserId");
        return mongoTemplate.find(query, BlacklistDoc.class).stream()
                .map(BlacklistDoc::getBlockUserId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<Blacklist> listBlacklist(String userId) {
        Query query = Query.query(Criteria.where("ownerUserId").is(userId));
        return mongoTemplate.find(query, BlacklistDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Blacklist toDomain(BlacklistDoc doc) {
        Blacklist blacklist = new Blacklist();
        blacklist.setId(doc.getId());
        blacklist.setOwnerUserId(doc.getOwnerUserId());
        blacklist.setBlockUserId(doc.getBlockUserId());
        blacklist.setAddSource(doc.getAddSource());
        blacklist.setOperatorUserId(doc.getOperatorUserId());
        blacklist.setEx(doc.getEx());
        blacklist.setCreatedAt(doc.getCreatedAt() != null ? doc.getCreatedAt() : 0L);
        return blacklist;
    }

    private static String docId(String ownerUserId, String blockUserId) {
        return ownerUserId + ":" + blockUserId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
