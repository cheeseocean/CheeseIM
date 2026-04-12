package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.api.enums.GroupMemberRoleEnum;
import com.cheeseocean.im.common.api.business.domain.GroupMember;
import com.cheeseocean.im.common.core.business.mongo.document.group.GroupMemberDoc;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link GroupMemberRepository} 的 MongoDB 实现。
 */
public class GroupMemberRepositoryImpl implements GroupMemberRepository {

    private final MongoTemplate mongoTemplate;

    public GroupMemberRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<GroupMember> findByGroupAndUser(String groupId, String userId) {
        Query query = Query.query(Criteria.where("_id").is(docId(groupId, userId)));
        return Optional.ofNullable(mongoTemplate.findOne(query, GroupMemberDoc.class)).map(this::toDomain);
    }

    @Override
    public Optional<GroupMember> findOwner(String groupId) {
        // 群主查询是管理链路常用入口，单独保留比外面再按角色过滤更直接。
        Query query = Query.query(Criteria.where("groupId").is(groupId).and("roleLevel").is(GroupMemberRoleEnum.OWNER.getCode()));
        return Optional.ofNullable(mongoTemplate.findOne(query, GroupMemberDoc.class)).map(this::toDomain);
    }

    @Override
    public List<GroupMember> findByGroupId(String groupId) {
        // 成员列表按角色优先，再按入群时间排序。
        Query query = Query.query(Criteria.where("groupId").is(groupId))
                .with(Sort.by(Sort.Order.desc("roleLevel"), Sort.Order.asc("joinTime")));
        return mongoTemplate.find(query, GroupMemberDoc.class).stream().map(this::toDomain).toList();
    }

    @Override
    public List<GroupMember> find(String groupId, List<String> userIds) {
        Query query = Query.query(Criteria.where("groupId").is(groupId));
        if (userIds != null && !userIds.isEmpty()) {
            query.addCriteria(Criteria.where("userId").in(userIds));
        }
        return mongoTemplate.find(query, GroupMemberDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public List<GroupMember> findInGroups(String userId, List<String> groupIds) {
        Query query = Query.query(Criteria.where("userId").is(userId));
        if (groupIds != null && !groupIds.isEmpty()) {
            query.addCriteria(Criteria.where("groupId").in(groupIds));
        }
        return mongoTemplate.find(query, GroupMemberDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public List<String> findGroupIdsByUserId(String userId) {
        Query query = Query.query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Order.desc("roleLevel"), Sort.Order.asc("joinTime")));
        query.fields().include("groupId");
        return mongoTemplate.find(query, GroupMemberDoc.class).stream()
                .map(GroupMemberDoc::getGroupId)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public List<String> findManagedGroupIds(String userId) {
        // owner/admin 两种角色都视为“管理的群”。
        Query query = Query.query(
                Criteria.where("userId").is(userId).and("roleLevel").in(
                        GroupMemberRoleEnum.OWNER.getCode(),
                        GroupMemberRoleEnum.ADMIN.getCode()
                )
        );
        query.fields().include("groupId");
        return mongoTemplate.find(query, GroupMemberDoc.class).stream()
                .map(GroupMemberDoc::getGroupId)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public List<GroupMember> findByGroupIdAndRole(String groupId, int roleLevel) {
        Query query = Query.query(Criteria.where("groupId").is(groupId).and("roleLevel").is(roleLevel));
        return mongoTemplate.find(query, GroupMemberDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public List<String> findRoleUserIds(String groupId, int roleLevel) {
        Query query = Query.query(Criteria.where("groupId").is(groupId).and("roleLevel").is(roleLevel));
        query.fields().include("userId");
        return mongoTemplate.find(query, GroupMemberDoc.class).stream()
                .map(GroupMemberDoc::getUserId)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public List<String> findMemberUserIds(String groupId) {
        Query query = Query.query(Criteria.where("groupId").is(groupId))
                .with(Sort.by(Sort.Order.desc("roleLevel"), Sort.Order.asc("joinTime")));
        query.fields().include("userId");
        return mongoTemplate.find(query, GroupMemberDoc.class).stream()
                .map(GroupMemberDoc::getUserId)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public List<GroupMember> searchMembers(String keyword, String groupId, int limit, int offset) {
        // 只在群维度内按群昵称搜索，不回退到全局用户昵称。
        Criteria criteria = Criteria.where("groupId").is(groupId);
        if (StringUtils.hasText(keyword)) {
            criteria = new Criteria().andOperator(criteria, Criteria.where("nickname").regex(keyword, "i"));
        }
        Query query = Query.query(criteria)
                .with(PageRequest.of(Math.max(0, offset) / pageSize(limit), pageSize(limit),
                        Sort.by(Sort.Order.desc("roleLevel"), Sort.Order.asc("joinTime"))));
        return mongoTemplate.find(query, GroupMemberDoc.class).stream()
                .map(this::toDomain)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public boolean existsByGroupAndUser(String groupId, String userId) {
        Query query = Query.query(Criteria.where("_id").is(docId(groupId, userId)));
        return mongoTemplate.exists(query, GroupMemberDoc.class);
    }

    @Override
    public long countByGroupId(String groupId) {
        return mongoTemplate.count(Query.query(Criteria.where("groupId").is(groupId)), GroupMemberDoc.class);
    }

    @Override
    public void saveAll(List<GroupMember> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        // 群成员是典型的批量写场景：建群拉人、批量入群、角色同步都走这里。
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, GroupMemberDoc.class);
        int operations = 0;
        for (GroupMember member : members) {
            if (member == null || !StringUtils.hasText(member.getGroupId()) || !StringUtils.hasText(member.getUserId())) {
                continue;
            }
            bulkOperations.upsert(
                    Query.query(Criteria.where("_id").is(docId(member.getGroupId(), member.getUserId()))),
                    toUpdate(member)
            );
            operations++;
        }
        if (operations > 0) {
            bulkOperations.execute();
        }
    }

    @Override
    public void updateFields(String groupId, String userId, Map<String, Object> fields) {
        if (!StringUtils.hasText(groupId) || !StringUtils.hasText(userId) || fields == null || fields.isEmpty()) {
            return;
        }
        Query query = Query.query(Criteria.where("_id").is(docId(groupId, userId)));
        Update update = new Update();
        fields.forEach((key, value) -> {
            if (key != null) {
                update.set(key, value);
            }
        });
        mongoTemplate.updateFirst(query, update, GroupMemberDoc.class);
    }

    @Override
    public void updateRoleLevel(String groupId, String userId, int roleLevel) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("roleLevel", roleLevel);
        updateFields(groupId, userId, fields);
    }

    @Override
    public void remove(String groupId, String userId) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(docId(groupId, userId))), GroupMemberDoc.class);
    }

    @Override
    public void removeAll(String groupId, List<String> userIds) {
        if (!StringUtils.hasText(groupId)) {
            return;
        }
        Query query = Query.query(Criteria.where("groupId").is(groupId));
        if (userIds != null && !userIds.isEmpty()) {
            query.addCriteria(Criteria.where("userId").in(userIds));
        }
        mongoTemplate.remove(query, GroupMemberDoc.class);
    }

    private GroupMember toDomain(GroupMemberDoc doc) {
        GroupMember member = new GroupMember();
        member.setId(doc.getId());
        member.setGroupId(doc.getGroupId());
        member.setUserId(doc.getUserId());
        member.setNickname(doc.getNickname());
        member.setAvatarUrl(doc.getAvatarUrl());
        member.setRoleLevel(GroupMemberRoleEnum.fromCode(doc.getRoleLevel()));
        member.setJoinSource(doc.getJoinSource());
        member.setInviterUserId(doc.getInviterUserId());
        member.setOperatorUserId(doc.getOperatorUserId());
        member.setMuteEndTime(doc.getMuteEndTime());
        member.setEx(doc.getEx());
        member.setJoinTime(doc.getJoinTime());
        return member;
    }

    private Update toUpdate(GroupMember member) {
        return new Update()
                .set("id", docId(member.getGroupId(), member.getUserId()))
                .set("groupId", member.getGroupId())
                .set("userId", member.getUserId())
                .set("nickname", member.getNickname())
                .set("faceUrl", member.getAvatarUrl())
                .set("roleLevel", member.getRoleLevel() != null ? member.getRoleLevel().getCode() : GroupMemberRoleEnum.MEMBER.getCode())
                .set("joinSource", member.getJoinSource())
                .set("inviterUserId", member.getInviterUserId())
                .set("operatorUserId", member.getOperatorUserId())
                .set("muteEndTime", member.getMuteEndTime())
                .set("ex", member.getEx())
                .setOnInsert("joinTime", member.getJoinTime());
    }

    private static String docId(String groupId, String userId) {
        return groupId + ":" + userId;
    }

    private static int pageSize(int limit) {
        return Math.max(1, limit <= 0 ? 50 : limit);
    }
}
