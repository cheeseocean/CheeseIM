package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.cache.redis.BatchCacheHelper;
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
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link GroupRepository} 的 MongoDB + Redis 实现。
 *
 * <p><b>缓存策略：</b>
 * <ul>
 *   <li>群组信息：{@code cheese_im:group_info:{groupId}} 缓存完整 Group 对象，TTL 12h，写时失效。</li>
 * </ul>
 *
 * <p>{@link #findByIds} 采用 batchGetCache2 模式：先批量读 Redis，未命中的
 * 一次性从 MongoDB 批量查询，再写回 Redis，最后按入参顺序返回结果。
 */
public class GroupRepositoryImpl implements GroupRepository {

    /** 群组信息缓存 TTL */
    private static final Duration GROUP_TTL = Duration.ofHours(12);

    private final GroupMongoRepository groupMongoRepository;
    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public GroupRepositoryImpl(GroupMongoRepository groupMongoRepository,
                               MongoTemplate mongoTemplate,
                               RedisTemplate<String, Object> redisTemplate) {
        this.groupMongoRepository = groupMongoRepository;
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
    }

    // ── 读操作 ────────────────────────────────────────────────────────────────

    @Override
    public Optional<Group> findById(String groupId) {
        return BatchCacheHelper.getCache(
                redisTemplate,
                RedisKeys.groupInfo(groupId),
                GROUP_TTL,
                () -> groupMongoRepository.findById(groupId).map(this::toDomain),
                Group.class
        );
    }

    /**
     * batchGetCache2：批量先查 Redis，未命中的从 MongoDB 一次批量补全，再写回缓存。
     */
    @Override
    public List<Group> findByIds(List<String> groupIds) {
        return BatchCacheHelper.batchGetCache2(
                redisTemplate,
                GROUP_TTL,
                groupIds,
                RedisKeys::groupInfo,
                Group::getGroupId,
                ids -> groupMongoRepository.findAllById(ids).stream()
                        .map(this::toDomain)
                        .collect(Collectors.toList()),
                Group.class
        );
    }

    @Override
    public boolean existsById(String groupId) {
        return groupMongoRepository.existsById(groupId);
    }

    // ── 写操作（写后失效缓存）────────────────────────────────────────────────

    @Override
    public void save(Group group) {
        groupMongoRepository.save(toDoc(group));
        redisTemplate.delete(RedisKeys.groupInfo(group.getGroupId()));
    }

    @Override
    public void updateFields(String groupId, Map<String, Object> fields) {
        Query query = Query.query(Criteria.where("_id").is(groupId));
        Update update = new Update();
        fields.forEach(update::set);
        mongoTemplate.updateFirst(query, update, GroupDoc.class);
        redisTemplate.delete(RedisKeys.groupInfo(groupId));
    }

    @Override
    public void disband(String groupId) {
        Query query = Query.query(Criteria.where("_id").is(groupId));
        Update update = new Update().set("status", GroupStatusEnum.DISBANDED.getCode());
        mongoTemplate.updateFirst(query, update, GroupDoc.class);
        redisTemplate.delete(RedisKeys.groupInfo(groupId));
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
