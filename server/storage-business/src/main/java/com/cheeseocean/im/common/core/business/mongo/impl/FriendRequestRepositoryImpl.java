package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.enums.HandleResultEnum;
import com.cheeseocean.im.common.api.business.domain.FriendRequest;
import com.cheeseocean.im.common.core.business.mongo.document.user.FriendRequestDoc;
import com.cheeseocean.im.common.core.business.repository.FriendRequestRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * {@link FriendRequestRepository} 的 MongoDB 实现。
 */
public class FriendRequestRepositoryImpl implements FriendRequestRepository {

    private final MongoTemplate mongoTemplate;

    public FriendRequestRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void saveAll(List<FriendRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        // 申请记录按 (fromUserId,toUserId) 唯一覆盖，重复申请直接刷新同一文档。
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, FriendRequestDoc.class);
        int operations = 0;
        for (FriendRequest request : requests) {
            if (request == null || isBlank(request.getFromUserId()) || isBlank(request.getToUserId())) {
                continue;
            }
            bulkOperations.upsert(
                    Query.query(Criteria.where("_id").is(docId(request.getFromUserId(), request.getToUserId()))),
                    toUpdate(request)
            );
            operations++;
        }
        if (operations > 0) {
            bulkOperations.execute();
        }
    }

    @Override
    public void updateFields(String fromUserId, String toUserId, Map<String, Object> fields) {
        if (isBlank(fromUserId) || isBlank(toUserId) || fields == null || fields.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(docId(fromUserId, toUserId)));
        Update update = new Update();
        fields.forEach((key, value) -> {
            if (key != null) {
                update.set(key, value);
            }
        });
        mongoTemplate.updateFirst(query, update, FriendRequestDoc.class);
    }

    @Override
    public void update(FriendRequest request) {
        if (request == null || isBlank(request.getFromUserId()) || isBlank(request.getToUserId())) {
            return;
        }
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(docId(request.getFromUserId(), request.getToUserId()))),
                toUpdate(request),
                FriendRequestDoc.class
        );
    }

    @Override
    public void delete(String fromUserId, String toUserId) {
        Query query = Query.query(Criteria.where("_id").is(docId(fromUserId, toUserId)));
        mongoTemplate.remove(query, FriendRequestDoc.class);
    }

    @Override
    public FriendRequest find(String fromUserId, String toUserId) {
        Query query = Query.query(Criteria.where("_id").is(docId(fromUserId, toUserId)));
        FriendRequestDoc doc = mongoTemplate.findOne(query, FriendRequestDoc.class);
        return doc == null ? null : toDomain(doc);
    }

    @Override
    public List<FriendRequest> findBothDirections(String userA, String userB) {
        // 同时查双向申请，处理“我申请过你 / 你申请过我”两个方向的冲突判断。
        Query query = Query.query(new Criteria().orOperator(
                Criteria.where("_id").is(docId(userA, userB)),
                Criteria.where("_id").is(docId(userB, userA))
        )).with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        return mongoTemplate.find(query, FriendRequestDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<FriendRequest> findIncoming(String toUserId, List<Integer> handleResults, int limit, int offset) {
        return findByDirection("toUserId", toUserId, handleResults, limit, offset);
    }

    @Override
    public List<FriendRequest> findOutgoing(String fromUserId, List<Integer> handleResults, int limit, int offset) {
        return findByDirection("fromUserId", fromUserId, handleResults, limit, offset);
    }

    @Override
    public long countUnhandled(String toUserId, long afterTs) {
        // afterTs 用于首页角标之类的增量未处理统计。
        Query query = Query.query(
                Criteria.where("toUserId").is(toUserId)
                        .and("handleResult").is(HandleResultEnum.PENDING.getCode())
                        .and("updatedAt").gt(afterTs)
        );
        return mongoTemplate.count(query, FriendRequestDoc.class);
    }

    private List<FriendRequest> findByDirection(
            String directionField,
            String userId,
            List<Integer> handleResults,
            int limit,
            int offset) {
        Criteria criteria = Criteria.where(directionField).is(userId);
        if (handleResults != null && !handleResults.isEmpty()) {
            criteria = criteria.and("handleResult").in(handleResults);
        }
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .skip(Math.max(0, offset));
        if (limit > 0) {
            query.limit(limit);
        }
        return mongoTemplate.find(query, FriendRequestDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Update toUpdate(FriendRequest request) {
        Update update = new Update()
                .set("fromUserId", request.getFromUserId())
                .set("toUserId", request.getToUserId())
                .set("handleResult", request.getHandleResult() != null ? request.getHandleResult().getCode() : 0)
                .set("reqMsg", request.getReqMsg())
                .set("handlerUserId", request.getHandlerUserId())
                .set("handleMsg", request.getHandleMsg())
                .set("handleTime", request.getHandleTime())
                .set("ex", request.getEx())
                .set("updatedAt", request.getUpdatedAt());
        if (request.getCreateTime() > 0) {
            update.setOnInsert("createTime", request.getCreateTime());
        }
        return update;
    }

    private FriendRequest toDomain(FriendRequestDoc doc) {
        FriendRequest request = new FriendRequest();
        request.setId(doc.getId());
        request.setFromUserId(doc.getFromUserId());
        request.setToUserId(doc.getToUserId());
        request.setHandleResult(HandleResultEnum.fromCode(doc.getHandleResult()));
        request.setReqMsg(doc.getReqMsg());
        request.setHandlerUserId(doc.getHandlerUserId());
        request.setHandleMsg(doc.getHandleMsg());
        request.setHandleTime(doc.getHandleTime());
        request.setEx(doc.getEx());
        request.setCreateTime(doc.getCreateTime());
        request.setUpdatedAt(doc.getUpdatedAt());
        return request;
    }

    private static String docId(String fromUserId, String toUserId) {
        return fromUserId + ":" + toUserId;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
