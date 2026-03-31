package com.cheeseocean.im.common.core.business.mongo.impl;

import com.cheeseocean.im.common.core.business.domain.GroupMember;
import com.cheeseocean.im.common.core.business.mongo.document.group.GroupMemberDoc;
import com.cheeseocean.im.common.core.business.mongo.repository.GroupMemberMongoRepository;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import com.cheeseocean.im.common.core.enums.GroupMemberRoleEnum;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@link GroupMemberRepository} 的 MongoDB 实现。
 */
public class GroupMemberRepositoryImpl implements GroupMemberRepository {

    private final GroupMemberMongoRepository groupMemberMongoRepository;

    public GroupMemberRepositoryImpl(GroupMemberMongoRepository groupMemberMongoRepository) {
        this.groupMemberMongoRepository = groupMemberMongoRepository;
    }

    @Override
    public Optional<GroupMember> findByGroupAndUser(String groupId, String userId) {
        return groupMemberMongoRepository.findByGroupIdAndUserId(groupId, userId)
                .map(this::toDomain);
    }

    @Override
    public List<GroupMember> findByGroupId(String groupId) {
        return groupMemberMongoRepository.findByGroupId(groupId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> findGroupIdsByUserId(String userId) {
        return groupMemberMongoRepository.findByUserId(userId).stream()
                .map(GroupMemberDoc::getGroupId)
                .collect(Collectors.toList());
    }

    @Override
    public List<GroupMember> findByGroupIdAndRole(String groupId, int roleLevel) {
        return groupMemberMongoRepository.findByGroupIdAndRoleLevel(groupId, roleLevel).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByGroupAndUser(String groupId, String userId) {
        return groupMemberMongoRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    @Override
    public long countByGroupId(String groupId) {
        return groupMemberMongoRepository.countByGroupId(groupId);
    }

    @Override
    public void save(GroupMember member) {
        groupMemberMongoRepository.save(toDoc(member));
    }

    @Override
    public void saveAll(List<GroupMember> members) {
        groupMemberMongoRepository.saveAll(
                members.stream().map(this::toDoc).collect(Collectors.toList()));
    }

    @Override
    public void remove(String groupId, String userId) {
        groupMemberMongoRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    @Override
    public void removeAll(String groupId, List<String> userIds) {
        groupMemberMongoRepository.deleteByGroupIdAndUserIdIn(groupId, userIds);
    }

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
