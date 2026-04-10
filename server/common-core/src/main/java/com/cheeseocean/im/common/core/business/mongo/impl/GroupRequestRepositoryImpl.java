package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.enums.HandleResultEnum;
import com.cheeseocean.im.common.api.business.domain.GroupRequest;
import com.cheeseocean.im.common.core.business.mongo.document.group.GroupRequestDoc;
import com.cheeseocean.im.common.core.business.repository.GroupRequestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link GroupRequestRepository} 的 MongoDB 实现。
 */
public class GroupRequestRepositoryImpl implements GroupRequestRepository {

    private final MongoTemplate mongoTemplate;

    public GroupRequestRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<GroupRequest> findByUserAndGroup(String userId, String groupId) {
        Query query = Query.query(Criteria.where("_id").is(docId(userId, groupId)));
        return Optional.ofNullable(mongoTemplate.findOne(query, GroupRequestDoc.class)).map(this::toDomain);
    }

    @Override
    public List<GroupRequest> findByGroup(String groupId, List<String> userIds) {
        Query query = Query.query(Criteria.where("groupId").is(groupId));
        if (userIds != null && !userIds.isEmpty()) {
            query.addCriteria(Criteria.where("userId").in(userIds));
        }
        return mongoTemplate.find(query, GroupRequestDoc.class).stream().map(this::toDomain).toList();
    }

    @Override
    public void saveAll(List<GroupRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        // 入群申请以 (userId,groupId) 唯一覆盖，重复申请刷新原记录。
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, GroupRequestDoc.class);
        int operations = 0;
        for (GroupRequest request : requests) {
            if (request == null || !StringUtils.hasText(request.getUserId()) || !StringUtils.hasText(request.getGroupId())) {
                continue;
            }
            bulkOperations.upsert(
                    Query.query(Criteria.where("_id").is(docId(request.getUserId(), request.getGroupId()))),
                    toUpdate(request)
            );
            operations++;
        }
        if (operations > 0) {
            bulkOperations.execute();
        }
    }

    @Override
    public void delete(String userId, String groupId) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(docId(userId, groupId))), GroupRequestDoc.class);
    }

    @Override
    public void updateFields(String userId, String groupId, Map<String, Object> fields) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(groupId) || fields == null || fields.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(docId(userId, groupId)));
        Update update = new Update();
        fields.forEach((key, value) -> {
            if (key != null) {
                update.set(key, value);
            }
        });
        mongoTemplate.updateFirst(query, update, GroupRequestDoc.class);
    }

    @Override
    public List<GroupRequest> pageByUser(String userId, List<String> groupIds, List<Integer> handleResults, int limit, int offset) {
        // 这是“某用户发起过哪些加群申请”的查询入口。
        Criteria criteria = Criteria.where("userId").is(userId);
        if (groupIds != null && !groupIds.isEmpty()) {
            criteria = criteria.and("groupId").in(groupIds);
        }
        if (handleResults != null && !handleResults.isEmpty()) {
            criteria = criteria.and("handleResult").in(handleResults);
        }
        Query query = Query.query(criteria)
                .with(PageRequest.of(Math.max(0, offset) / pageSize(limit), pageSize(limit), Sort.by(Sort.Direction.DESC, "reqTime")));
        return mongoTemplate.find(query, GroupRequestDoc.class).stream().map(this::toDomain).toList();
    }

    @Override
    public List<GroupRequest> pageByGroups(List<String> groupIds, List<Integer> handleResults, int limit, int offset) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        // 这是“我管理的群收到哪些申请”的查询入口。
        Criteria criteria = Criteria.where("groupId").in(groupIds);
        if (handleResults != null && !handleResults.isEmpty()) {
            criteria = criteria.and("handleResult").in(handleResults);
        }
        Query query = Query.query(criteria)
                .with(PageRequest.of(Math.max(0, offset) / pageSize(limit), pageSize(limit), Sort.by(Sort.Direction.DESC, "reqTime")));
        return mongoTemplate.find(query, GroupRequestDoc.class).stream().map(this::toDomain).toList();
    }

    @Override
    public long countUnhandled(List<String> groupIds, long afterTs) {
        if (groupIds == null || groupIds.isEmpty()) {
            return 0L;
        }
        // 角标统计只关心待处理申请，afterTs 用于增量窗口。
        Criteria criteria = Criteria.where("groupId").in(groupIds).and("handleResult").is(HandleResultEnum.PENDING.getCode());
        if (afterTs > 0) {
            criteria = criteria.and("reqTime").gt(afterTs);
        }
        return mongoTemplate.count(Query.query(criteria), GroupRequestDoc.class);
    }

    private GroupRequest toDomain(GroupRequestDoc doc) {
        GroupRequest request = new GroupRequest();
        request.setId(doc.getId());
        request.setUserId(doc.getUserId());
        request.setGroupId(doc.getGroupId());
        request.setHandleResult(HandleResultEnum.fromCode(doc.getHandleResult()));
        request.setReqMsg(doc.getReqMsg());
        request.setHandledMsg(doc.getHandledMsg());
        request.setHandleUserId(doc.getHandleUserId());
        request.setHandledTime(doc.getHandledTime());
        request.setJoinSource(doc.getJoinSource());
        request.setInviterUserId(doc.getInviterUserId());
        request.setEx(doc.getEx());
        request.setReqTime(doc.getReqTime());
        return request;
    }

    private Update toUpdate(GroupRequest request) {
        return new Update()
                .set("userId", request.getUserId())
                .set("groupId", request.getGroupId())
                .set("handleResult", request.getHandleResult() != null ? request.getHandleResult().getCode() : HandleResultEnum.PENDING.getCode())
                .set("reqMsg", request.getReqMsg())
                .set("handledMsg", request.getHandledMsg())
                .set("handleUserId", request.getHandleUserId())
                .set("handledTime", request.getHandledTime())
                .set("joinSource", request.getJoinSource())
                .set("inviterUserId", request.getInviterUserId())
                .set("ex", request.getEx())
                .setOnInsert("reqTime", request.getReqTime());
    }

    private static String docId(String userId, String groupId) {
        return userId + ":" + groupId;
    }

    private static int pageSize(int limit) {
        return Math.max(1, limit <= 0 ? 50 : limit);
    }
}
