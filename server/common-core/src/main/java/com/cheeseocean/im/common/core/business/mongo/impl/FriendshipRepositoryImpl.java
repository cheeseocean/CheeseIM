package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.business.domain.Friendship;
import com.cheeseocean.im.common.core.business.mongo.document.user.FriendshipDoc;
import com.cheeseocean.im.common.core.business.repository.FriendshipRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;

/**
 * {@link FriendshipRepository} 的 MongoDB 实现。
 */
public class FriendshipRepositoryImpl implements FriendshipRepository {

    private final MongoTemplate mongoTemplate;

    public FriendshipRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void saveAll(List<Friendship> friendships) {
        if (friendships == null || friendships.isEmpty()) {
            return;
        }
        // 好友关系天然是双向写扩散，批量 upsert 便于一次性落两条视角记录。
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, FriendshipDoc.class);
        int operations = 0;
        for (Friendship friendship : friendships) {
            if (friendship == null || isBlank(friendship.getUserId()) || isBlank(friendship.getFriendId())) {
                continue;
            }
            bulkOperations.upsert(
                    Query.query(Criteria.where("_id").is(docId(friendship.getUserId(), friendship.getFriendId()))),
                    toUpsert(friendship)
            );
            operations++;
        }
        if (operations > 0) {
            bulkOperations.execute();
        }
    }

    @Override
    public void delete(String ownerUserId, List<String> friendUserIds) {
        if (isBlank(ownerUserId) || friendUserIds == null || friendUserIds.isEmpty()) {
            return;
        }
        Query query = Query.query(
                Criteria.where("ownerUserId").is(ownerUserId).and("friendUserId").in(friendUserIds)
        );
        mongoTemplate.remove(query, FriendshipDoc.class);
    }

    @Override
    public void updateFields(String ownerUserId, String friendUserId, Map<String, Object> fields) {
        if (isBlank(ownerUserId) || isBlank(friendUserId) || fields == null || fields.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, friendUserId)));
        Update update = new Update();
        fields.forEach((key, value) -> {
            if (key != null) {
                update.set(key, value);
            }
        });
        mongoTemplate.updateFirst(query, update, FriendshipDoc.class);
    }

    @Override
    public void updateBatchFields(String ownerUserId, List<String> friendUserIds, Map<String, Object> fields) {
        if (isBlank(ownerUserId) || friendUserIds == null || friendUserIds.isEmpty() || fields == null || fields.isEmpty()) {
            return;
        }
        Query query = Query.query(
                Criteria.where("ownerUserId").is(ownerUserId).and("friendUserId").in(friendUserIds)
        );
        Update update = new Update();
        fields.forEach((key, value) -> {
            if (key != null) {
                update.set(key, value);
            }
        });
        mongoTemplate.updateMulti(query, update, FriendshipDoc.class);
    }

    @Override
    public Friendship find(String ownerUserId, String friendUserId) {
        Query query = Query.query(Criteria.where("_id").is(docId(ownerUserId, friendUserId)));
        FriendshipDoc doc = mongoTemplate.findOne(query, FriendshipDoc.class);
        return doc == null ? null : toDomain(doc);
    }

    @Override
    public List<Friendship> findFriends(String ownerUserId, List<String> friendUserIds) {
        if (isBlank(ownerUserId) || friendUserIds == null || friendUserIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(
                Criteria.where("ownerUserId").is(ownerUserId).and("friendUserId").in(friendUserIds)
        );
        return mongoTemplate.find(query, FriendshipDoc.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Friendship> findReverseFriends(String friendUserId, List<String> ownerUserIds) {
        if (isBlank(friendUserId) || ownerUserIds == null || ownerUserIds.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(
                Criteria.where("friendUserId").is(friendUserId).and("ownerUserId").in(ownerUserIds)
        );
        return mongoTemplate.find(query, FriendshipDoc.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Friendship> findOwnerFriends(String ownerUserId, int limit, int offset) {
        // 好友列表先按置顶，再按建立关系时间排序。
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId))
                .with(Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("createdAt")))
                .skip(Math.max(0, offset));
        if (limit > 0) {
            query.limit(limit);
        }
        return mongoTemplate.find(query, FriendshipDoc.class).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<String> findOwnerFriendUserIds(String ownerUserId, int limit) {
        // 这里只投影 friendUserId，给上层做轻量好友列表或关系存在性判断。
        Query query = Query.query(Criteria.where("ownerUserId").is(ownerUserId))
                .with(Sort.by(Sort.Order.desc("isPinned"), Sort.Order.desc("createdAt")));
        if (limit > 0) {
            query.limit(limit);
        }
        query.fields().include("friendUserId");
        return mongoTemplate.find(query, FriendshipDoc.class).stream()
                .map(FriendshipDoc::getFriendId)
                .toList();
    }

    @Override
    public List<String> findOwnersByFriendUserId(String friendUserId) {
        Query query = Query.query(Criteria.where("friendUserId").is(friendUserId));
        query.fields().include("ownerUserId");
        return mongoTemplate.find(query, FriendshipDoc.class).stream()
                .map(FriendshipDoc::getUserId)
                .toList();
    }

    private Update toUpsert(Friendship friendship) {
        Update update = new Update()
                .set("ownerUserId", friendship.getUserId())
                .set("friendUserId", friendship.getFriendId())
                .set("remark", friendship.getRemark())
                .set("addSource", friendship.getAddSource())
                .set("operatorUserId", friendship.getOperatorId())
                .set("isPinned", friendship.isPinned())
                .set("ex", friendship.getEx());
        if (friendship.getCreatedAt() > 0) {
            update.setOnInsert("createdAt", friendship.getCreatedAt());
        }
        return update;
    }

    private Friendship toDomain(FriendshipDoc doc) {
        Friendship friendship = new Friendship();
        friendship.setId(doc.getId());
        friendship.setUserId(doc.getUserId());
        friendship.setFriendId(doc.getFriendId());
        friendship.setRemark(doc.getRemark());
        friendship.setAddSource(doc.getAddSource());
        friendship.setOperatorId(doc.getOperatorId());
        friendship.setPinned(doc.isPinned());
        friendship.setEx(doc.getEx());
        friendship.setCreatedAt(doc.getCreatedAt() != null ? doc.getCreatedAt() : 0L);
        return friendship;
    }

    private static String docId(String ownerUserId, String friendUserId) {
        return ownerUserId + ":" + friendUserId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
