package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.enums.GroupStatusEnum;
import com.cheeseocean.im.common.core.enums.GroupTypeEnum;
import com.cheeseocean.im.common.core.enums.NeedVerificationEnum;
import com.cheeseocean.im.common.core.business.domain.Group;
import com.cheeseocean.im.common.core.business.mongo.document.group.GroupDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.GroupMongoRepository;
import com.cheeseocean.im.common.core.business.repository.GroupRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link GroupRepository} 的 MongoDB 实现。
 */
public class GroupRepositoryImpl implements GroupRepository {

    private final GroupMongoRepository groupMongoRepository;
    private final MongoTemplate mongoTemplate;

    public GroupRepositoryImpl(GroupMongoRepository groupMongoRepository,
                               MongoTemplate mongoTemplate) {
        this.groupMongoRepository = groupMongoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // ── 读操作 ────────────────────────────────────────────────────────────────

    @Override
    public Optional<Group> findById(String groupId) {
        return groupMongoRepository.findById(groupId).map(this::toDomain);
    }

    @Override
    public List<Group> findByIds(List<String> groupIds) {
        return groupMongoRepository.findAllById(groupIds).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(String groupId) {
        return groupMongoRepository.existsById(groupId);
    }

    // ── 写操作 ────────────────────────────────────────────────────────────────

    @Override
    public void save(Group group) {
        groupMongoRepository.save(toDoc(group));
    }

    @Override
    public void updateFields(String groupId, Map<String, Object> fields) {
        Query query = Query.query(Criteria.where("_id").is(groupId));
        Update update = new Update();
        fields.forEach(update::set);
        mongoTemplate.updateFirst(query, update, GroupDoc.class);
    }

    @Override
    public void disband(String groupId) {
        Query query = Query.query(Criteria.where("_id").is(groupId));
        Update update = new Update().set("status", GroupStatusEnum.DISBANDED.getCode());
        mongoTemplate.updateFirst(query, update, GroupDoc.class);
    }

    // ── 转换方法 ─────────────────────────────────────────────────────────────

    private Group toDomain(GroupDoc doc) {
        Group group = new Group();
        group.setGroupId(doc.getGroupId());
        group.setGroupName(doc.getGroupName());
        group.setNotification(doc.getNotification());
        group.setIntroduction(doc.getIntroduction());
        group.setFaceUrl(doc.getFaceUrl());
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

    private GroupDoc toDoc(Group group) {
        GroupDoc doc = new GroupDoc();
        doc.setGroupId(group.getGroupId());
        doc.setGroupName(group.getGroupName());
        doc.setNotification(group.getNotification());
        doc.setIntroduction(group.getIntroduction());
        doc.setFaceUrl(group.getFaceUrl());
        doc.setEx(group.getEx());
        doc.setStatus(group.getStatus() != null ? group.getStatus().getCode() : GroupStatusEnum.NORMAL.getCode());
        doc.setCreatorUserId(group.getCreatorUserId());
        doc.setGroupType(group.getGroupType() != null ? group.getGroupType().getCode() : 0);
        doc.setNeedVerification(group.getNeedVerification() != null ? group.getNeedVerification().getCode() : 0);
        doc.setLookMemberInfo(group.getLookMemberInfo());
        doc.setApplyMemberFriend(group.getApplyMemberFriend());
        doc.setNotificationUpdateTime(group.getNotificationUpdateTime());
        doc.setNotificationUserId(group.getNotificationUserId());
        doc.setCreateTime(group.getCreateTime());
        return doc;
    }
}
