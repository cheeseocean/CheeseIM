package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.enums.GroupStatusEnum;
import com.cheeseocean.im.common.api.enums.GroupTypeEnum;
import com.cheeseocean.im.common.api.enums.NeedVerificationEnum;
import com.cheeseocean.im.common.api.business.domain.Group;
import com.cheeseocean.im.common.core.business.mongo.document.group.GroupDoc;
import com.cheeseocean.im.common.core.business.repository.GroupRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link GroupRepository} 的 MongoDB 实现。
 */
public class GroupRepositoryImpl implements GroupRepository {

    private final MongoTemplate mongoTemplate;

    public GroupRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Group> findById(String groupId) {
        Query query = Query.query(Criteria.where("_id").is(groupId));
        return Optional.ofNullable(mongoTemplate.findOne(query, GroupDoc.class)).map(this::toDomain);
    }

    @Override
    public List<Group> findByIds(List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return new ArrayList<>();
        }
        Query query = Query.query(Criteria.where("_id").in(groupIds));
        return mongoTemplate.find(query, GroupDoc.class).stream().map(this::toDomain).toList();
    }

    @Override
    public void saveAll(List<Group> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        // 群资料写入统一走批量 upsert，便于兼容创建和后续资料同步。
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, GroupDoc.class);
        int operations = 0;
        for (Group group : groups) {
            if (group == null || !StringUtils.hasText(group.getGroupId())) {
                continue;
            }
            bulkOperations.upsert(
                    Query.query(Criteria.where("_id").is(group.getGroupId())),
                    toUpdate(group)
            );
            operations++;
        }
        if (operations > 0) {
            bulkOperations.execute();
        }
    }

    @Override
    public void updateFields(String groupId, Map<String, Object> fields) {
        if (!StringUtils.hasText(groupId) || fields == null || fields.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(groupId));
        Update update = new Update();
        fields.forEach((key, value) -> {
            if (key != null) {
                update.set(key, value);
            }
        });
        mongoTemplate.updateFirst(query, update, GroupDoc.class);
    }

    @Override
    public boolean exists(String groupId) {
        Query query = Query.query(Criteria.where("_id").is(groupId));
        return mongoTemplate.exists(query, GroupDoc.class);
    }

    @Override
    public void updateStatus(String groupId, int status) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", status);
        updateFields(groupId, fields);
    }

    @Override
    public List<Group> pageByKeyword(String keyword, int limit, int offset) {
        // 搜索默认过滤已解散群，避免客户端再二次过滤无效群。
        Query query = buildSearchQuery(keyword, null)
                .with(PageRequest.of(Math.max(0, offset) / pageSize(limit), pageSize(limit), Sort.by(Sort.Direction.DESC, "createTime")));
        return mongoTemplate.find(query, GroupDoc.class).stream().map(this::toDomain).toList();
    }

    @Override
    public long countByKeyword(String keyword) {
        return mongoTemplate.count(buildSearchQuery(keyword, null), GroupDoc.class);
    }

    @Override
    public List<String> findJoinSortedGroupIds(List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 已加入群列表按群名 + 创建时间稳定排序，方便客户端复用。
        Query query = buildSearchQuery(null, groupIds)
                .with(Sort.by(Sort.Order.asc("groupName"), Sort.Order.asc("createTime")));
        query.fields().include("groupId");
        return mongoTemplate.find(query, GroupDoc.class).stream().map(GroupDoc::getGroupId).toList();
    }

    @Override
    public List<Group> pageJoinedGroups(List<String> groupIds, String keyword, int limit, int offset) {
        if (groupIds == null || groupIds.isEmpty()) {
            return new ArrayList<>();
        }
        Query query = buildSearchQuery(keyword, groupIds)
                .with(PageRequest.of(Math.max(0, offset) / pageSize(limit), pageSize(limit),
                        Sort.by(Sort.Order.asc("groupName"), Sort.Order.asc("createTime"))));
        return mongoTemplate.find(query, GroupDoc.class).stream().map(this::toDomain).toList();
    }

    private Query buildSearchQuery(String keyword, List<String> groupIds) {
        // joined/search 两条路径都复用同一套状态过滤和关键词匹配规则。
        Criteria criteria = Criteria.where("status").ne(GroupStatusEnum.DISBANDED.getCode());
        if (groupIds != null) {
            criteria = criteria.and("groupId").in(groupIds);
        }
        if (StringUtils.hasText(keyword)) {
            criteria = new Criteria().andOperator(
                    criteria,
                    Criteria.where("groupName").regex(keyword, "i")
            );
        }
        return Query.query(criteria);
    }

    private Group toDomain(GroupDoc doc) {
        Group group = new Group();
        group.setGroupId(doc.getGroupId());
        group.setGroupName(doc.getGroupName());
        group.setNotification(doc.getNotification());
        group.setIntroduction(doc.getIntroduction());
        group.setAvatarUrl(doc.getAvatarUrl());
        group.setEx(doc.getEx());
        group.setStatus(GroupStatusEnum.fromCode(doc.getStatus()));
        group.setCreatorUserId(doc.getCreatorUserId());
        group.setGroupType(GroupTypeEnum.fromCode(doc.getGroupType()));
        group.setNeedVerification(NeedVerificationEnum.fromCode(doc.getNeedVerification()));
        group.setLookMemberInfo(doc.getLookMemberInfo());
        group.setApplyMemberFriend(doc.getApplyMemberFriend());
        group.setNotificationUpdateTime(doc.getNotificationUpdateTime());
        group.setNotificationUserId(doc.getNotificationUserId());
        group.setCreateTime(doc.getCreateTime());
        return group;
    }

    private Update toUpdate(Group group) {
        return new Update()
                .set("groupId", group.getGroupId())
                .set("groupName", group.getGroupName())
                .set("notification", group.getNotification())
                .set("introduction", group.getIntroduction())
                .set("faceUrl", group.getAvatarUrl())
                .set("ex", group.getEx())
                .set("status", group.getStatus() != null ? group.getStatus().getCode() : GroupStatusEnum.NORMAL.getCode())
                .set("creatorUserId", group.getCreatorUserId())
                .set("groupType", group.getGroupType() != null ? group.getGroupType().getCode() : 0)
                .set("needVerification", group.getNeedVerification() != null ? group.getNeedVerification().getCode() : 0)
                .set("lookMemberInfo", group.getLookMemberInfo())
                .set("applyMemberFriend", group.getApplyMemberFriend())
                .set("notificationUpdateTime", group.getNotificationUpdateTime())
                .set("notificationUserId", group.getNotificationUserId())
                .setOnInsert("createTime", group.getCreateTime());
    }

    private static int pageSize(int limit) {
        return Math.max(1, limit <= 0 ? 50 : limit);
    }
}
