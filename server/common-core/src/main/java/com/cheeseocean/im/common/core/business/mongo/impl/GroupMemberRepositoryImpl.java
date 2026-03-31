package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.cache.redis.BatchCacheHelper;
import com.cheeseocean.im.common.core.cache.redis.StringSetCacheHelper;
import com.cheeseocean.im.common.core.enums.GroupMemberRoleEnum;
import com.cheeseocean.im.common.core.business.domain.GroupMember;
import com.cheeseocean.im.common.core.business.mongo.document.group.GroupMemberDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.GroupMemberMongoRepository;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link GroupMemberRepository} 的 MongoDB + Redis 实现。
 *
 * <p><b>缓存策略：</b>
 * <ul>
 *   <li>成员信息：{@code cheese_im:group_member_info:{groupId}:{userId}} 缓存完整对象，TTL 12h，写时失效。</li>
 *   <li>成员 ID 集合：{@code cheese_im:group_member_ids:{groupId}} Redis SET，无 TTL，写时维护。</li>
 *   <li>角色成员 ID：{@code cheese_im:group_role_members:{groupId}:{roleLevel}} Redis SET，写时失效。</li>
 *   <li>用户已加入群：{@code cheese_im:user_joined_groups:{userId}} Redis SET，无 TTL，写时维护。</li>
 *   <li>成员数量：{@code cheese_im:group_member_num:{groupId}} String 整数，TTL 12h，写时失效。</li>
 * </ul>
 */
public class GroupMemberRepositoryImpl implements GroupMemberRepository {

    /** 成员信息与数量缓存 TTL */
    private static final Duration MEMBER_TTL = Duration.ofHours(12);

    private final GroupMemberMongoRepository groupMemberMongoRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public GroupMemberRepositoryImpl(GroupMemberMongoRepository groupMemberMongoRepository,
                                     RedisTemplate<String, Object> redisTemplate,
                                     StringRedisTemplate stringRedisTemplate) {
        this.groupMemberMongoRepository = groupMemberMongoRepository;
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // ── 读操作 ────────────────────────────────────────────────────────────────

    @Override
    public Optional<GroupMember> findByGroupAndUser(String groupId, String userId) {
        return BatchCacheHelper.getCache(
                redisTemplate,
                RedisKeys.groupMemberInfo(groupId, userId),
                MEMBER_TTL,
                () -> groupMemberMongoRepository.findByGroupIdAndUserId(groupId, userId)
                        .map(this::toDomain),
                GroupMember.class
        );
    }

    /**
     * 查询群内全部成员：先从 Redis SET 取成员 ID，再 batchGetCache2 取成员详情。
     * ID SET 不存在时回源 MongoDB 并全量回填缓存。
     */
    @Override
    public List<GroupMember> findByGroupId(String groupId) {
        List<String> memberIds = StringSetCacheHelper.getOrLoad(
                redisTemplate,
                RedisKeys.groupMemberIds(groupId),
                RedisKeys.groupMemberIdsLoaded(groupId),
                () -> groupMemberMongoRepository.findByGroupId(groupId).stream()
                        .map(GroupMemberDoc::getUserId)
                        .collect(Collectors.toList())
        );
        return batchGetMemberInfos(groupId, memberIds);
    }

    /**
     * 查询用户已加入的群组 ID 列表：优先读 Redis SET，缓存未命中时回源 MongoDB。
     */
    @Override
    public List<String> findGroupIdsByUserId(String userId) {
        return StringSetCacheHelper.getOrLoad(
                redisTemplate,
                RedisKeys.userJoinedGroupIds(userId),
                RedisKeys.userJoinedGroupIdsLoaded(userId),
                () -> groupMemberMongoRepository.findByUserId(userId).stream()
                        .map(GroupMemberDoc::getGroupId)
                        .collect(Collectors.toList())
        );
    }

    /**
     * 查询群内指定角色的成员列表（如群主、管理员）。
     * 优先读角色成员 ID 的 Redis SET，命中后 batchGetCache2 取详情；
     * 未命中时回源 MongoDB 并回填角色 SET 和成员详情缓存。
     */
    @Override
    public List<GroupMember> findByGroupIdAndRole(String groupId, int roleLevel) {
        String roleKey = RedisKeys.groupRoleLevelMemberIds(groupId, roleLevel);
        Set<Object> cachedIds = redisTemplate.opsForSet().members(roleKey);
        if (cachedIds != null && !cachedIds.isEmpty()) {
            List<String> memberIds = cachedIds.stream().map(String::valueOf).collect(Collectors.toList());
            return batchGetMemberInfos(groupId, memberIds);
        }
        // 缓存未命中：查 MongoDB，回填角色 SET 与成员详情
        List<GroupMember> members = groupMemberMongoRepository
                .findByGroupIdAndRoleLevel(groupId, roleLevel).stream()
                .map(this::toDomain).collect(Collectors.toList());
        if (!members.isEmpty()) {
            Object[] userIds = members.stream().map(GroupMember::getUserId).toArray();
            redisTemplate.opsForSet().add(roleKey, userIds);
            members.forEach(this::putMemberToCache);
        }
        return members;
    }

    @Override
    public boolean existsByGroupAndUser(String groupId, String userId) {
        // 先尝试通过单条 cache-aside 读取判断，避免 MongoDB 查询
        if (findByGroupAndUser(groupId, userId).isPresent()) {
            return true;
        }
        return groupMemberMongoRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    /**
     * 统计群成员数量：优先读 Redis 字符串缓存，缓存未命中时回源 MongoDB 并写回。
     */
    @Override
    public long countByGroupId(String groupId) {
        String val = stringRedisTemplate.opsForValue().get(RedisKeys.groupMemberNum(groupId));
        if (val != null) {
            return Long.parseLong(val);
        }
        long count = groupMemberMongoRepository.countByGroupId(groupId);
        stringRedisTemplate.opsForValue().set(
                RedisKeys.groupMemberNum(groupId), String.valueOf(count), MEMBER_TTL);
        return count;
    }

    // ── 写操作（写后更新缓存）────────────────────────────────────────────────

    @Override
    public void save(GroupMember member) {
        groupMemberMongoRepository.save(toDoc(member));
        updateCacheOnSave(member);
    }

    @Override
    public void saveAll(List<GroupMember> members) {
        groupMemberMongoRepository.saveAll(
                members.stream().map(this::toDoc).collect(Collectors.toList()));
        members.forEach(this::updateCacheOnSave);
    }

    @Override
    public void remove(String groupId, String userId) {
        groupMemberMongoRepository.deleteByGroupIdAndUserId(groupId, userId);
        evictCacheOnRemove(groupId, userId);
    }

    @Override
    public void removeAll(String groupId, List<String> userIds) {
        groupMemberMongoRepository.deleteByGroupIdAndUserIdIn(groupId, userIds);
        userIds.forEach(uid -> evictCacheOnRemove(groupId, uid));
    }

    // ── 缓存维护私有方法 ──────────────────────────────────────────────────────

    /**
     * 保存成员后更新缓存：
     * - 写入/覆盖成员详情缓存
     * - 向 GROUP_MEMBER_IDS SET 追加（如 SET 已存在）
     * - 向 USER_JOINED_GROUPS SET 追加（如 SET 已存在）
     * - 失效成员数量缓存（数量已变化）
     * - 失效所有角色成员 ID 缓存（角色可能变化）
     */
    private void updateCacheOnSave(GroupMember member) {
        String groupId = member.getGroupId();
        String userId  = member.getUserId();

        // 覆盖成员详情
        putMemberToCache(member);

        // 成员 ID SET：仅在 SET 已存在时追加，避免制造残缺集合
        Boolean memberIdsExist = redisTemplate.hasKey(RedisKeys.groupMemberIds(groupId));
        if (Boolean.TRUE.equals(memberIdsExist)) {
            redisTemplate.opsForSet().add(RedisKeys.groupMemberIds(groupId), userId);
            StringSetCacheHelper.markLoaded(redisTemplate, RedisKeys.groupMemberIdsLoaded(groupId));
        }

        // 用户已加入群 SET
        Boolean joinedExist = redisTemplate.hasKey(RedisKeys.userJoinedGroupIds(userId));
        if (Boolean.TRUE.equals(joinedExist)) {
            redisTemplate.opsForSet().add(RedisKeys.userJoinedGroupIds(userId), groupId);
            StringSetCacheHelper.markLoaded(redisTemplate, RedisKeys.userJoinedGroupIdsLoaded(userId));
        }

        // 失效数量缓存（新增/更新成员时数量可能变化）
        stringRedisTemplate.delete(RedisKeys.groupMemberNum(groupId));

        // 失效所有角色成员 ID 缓存（成员角色可能改变）
        evictRoleCache(groupId);
    }

    /**
     * 删除成员后失效相关缓存：
     * - 删除成员详情缓存
     * - 从 GROUP_MEMBER_IDS SET 移除
     * - 从 USER_JOINED_GROUPS SET 移除
     * - 失效成员数量缓存
     * - 失效所有角色成员 ID 缓存
     */
    private void evictCacheOnRemove(String groupId, String userId) {
        redisTemplate.delete(RedisKeys.groupMemberInfo(groupId, userId));
        redisTemplate.opsForSet().remove(RedisKeys.groupMemberIds(groupId), userId);
        redisTemplate.opsForSet().remove(RedisKeys.userJoinedGroupIds(userId), groupId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.groupMemberIdsLoaded(groupId)))) {
            StringSetCacheHelper.markLoaded(redisTemplate, RedisKeys.groupMemberIdsLoaded(groupId));
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.userJoinedGroupIdsLoaded(userId)))) {
            StringSetCacheHelper.markLoaded(redisTemplate, RedisKeys.userJoinedGroupIdsLoaded(userId));
        }
        stringRedisTemplate.delete(RedisKeys.groupMemberNum(groupId));
        evictRoleCache(groupId);
    }

    /** 失效群组内所有角色的成员 ID 缓存（角色成员变化时统一失效） */
    private void evictRoleCache(String groupId) {
        for (GroupMemberRoleEnum role : GroupMemberRoleEnum.values()) {
            redisTemplate.delete(RedisKeys.groupRoleLevelMemberIds(groupId, role.getCode()));
        }
    }

    /**
     * 全量回填成员缓存（findByGroupId MongoDB 回源后调用）：
     * - 写入所有成员详情
     * - 重建 GROUP_MEMBER_IDS SET
     */
    private void populateMemberCache(String groupId, List<GroupMember> members) {
        members.forEach(this::putMemberToCache);
        redisTemplate.delete(RedisKeys.groupMemberIds(groupId));
        if (!members.isEmpty()) {
            Object[] userIds = members.stream().map(GroupMember::getUserId).toArray();
            redisTemplate.opsForSet().add(RedisKeys.groupMemberIds(groupId), userIds);
        }
        StringSetCacheHelper.markLoaded(redisTemplate, RedisKeys.groupMemberIdsLoaded(groupId));
    }

    /**
     * batchGetCache2：批量先查 Redis，未命中的从 MongoDB 批量补全，再写回缓存。
     *
     * @param groupId   群组 ID
     * @param memberIds 需要查询的成员 userId 列表
     */
    private List<GroupMember> batchGetMemberInfos(String groupId, List<String> memberIds) {
        return BatchCacheHelper.batchGetCache2(
                redisTemplate,
                MEMBER_TTL,
                memberIds,
                uid -> RedisKeys.groupMemberInfo(groupId, uid),
                GroupMember::getUserId,
                ids -> groupMemberMongoRepository.findByGroupIdAndUserIdIn(groupId, ids).stream()
                        .map(this::toDomain)
                        .collect(Collectors.toList()),
                GroupMember.class
        );
    }

    private void putMemberToCache(GroupMember member) {
        redisTemplate.opsForValue().set(
                RedisKeys.groupMemberInfo(member.getGroupId(), member.getUserId()),
                member, MEMBER_TTL);
    }

    // ── 转换方法 ─────────────────────────────────────────────────────────────

    private GroupMember toDomain(GroupMemberDoc doc) {
        GroupMember member = new GroupMember();
        member.setId(doc.getId());
        member.setGroupId(doc.getGroupId());
        member.setUserId(doc.getUserId());
        member.setNickname(doc.getNickname());
        member.setFaceUrl(doc.getFaceUrl());
        member.setRoleLevel(GroupMemberRoleEnum.fromCode(doc.getRoleLevel()));
        member.setJoinSource(doc.getJoinSource());
        member.setInviterUserId(doc.getInviterUserId());
        member.setOperatorUserId(doc.getOperatorUserId());
        member.setMuteEndTime(doc.getMuteEndTime());
        member.setEx(doc.getEx());
        member.setJoinTime(doc.getJoinTime());
        return member;
    }

    private GroupMemberDoc toDoc(GroupMember member) {
        GroupMemberDoc doc = new GroupMemberDoc();
        doc.setId(member.getId() != null
                ? member.getId()
                : member.getGroupId() + ":" + member.getUserId());
        doc.setGroupId(member.getGroupId());
        doc.setUserId(member.getUserId());
        doc.setNickname(member.getNickname());
        doc.setFaceUrl(member.getFaceUrl());
        doc.setRoleLevel(member.getRoleLevel() != null
                ? member.getRoleLevel().getCode()
                : GroupMemberRoleEnum.MEMBER.getCode());
        doc.setJoinSource(member.getJoinSource());
        doc.setInviterUserId(member.getInviterUserId());
        doc.setOperatorUserId(member.getOperatorUserId());
        doc.setMuteEndTime(member.getMuteEndTime());
        doc.setEx(member.getEx());
        doc.setJoinTime(member.getJoinTime());
        return doc;
    }
}
