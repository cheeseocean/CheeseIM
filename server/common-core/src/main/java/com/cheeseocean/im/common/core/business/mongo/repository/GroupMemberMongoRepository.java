package com.cheeseocean.im.common.core.business.mongo.repository;

import com.cheeseocean.im.common.core.business.mongo.document.group.GroupMemberDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/** group_member 集合的 Spring Data 访问接口 */
public interface GroupMemberMongoRepository extends MongoRepository<GroupMemberDoc, String> {

    Optional<GroupMemberDoc> findByGroupIdAndUserId(String groupId, String userId);

    List<GroupMemberDoc> findByGroupId(String groupId);

    List<GroupMemberDoc> findByUserId(String userId);

    List<GroupMemberDoc> findByGroupIdAndRoleLevel(String groupId, int roleLevel);

    boolean existsByGroupIdAndUserId(String groupId, String userId);

    long countByGroupId(String groupId);

    void deleteByGroupIdAndUserId(String groupId, String userId);

    void deleteByGroupIdAndUserIdIn(String groupId, List<String> userIds);

    /** 批量查询群内指定成员（用于缓存 miss 时补全） */
    List<GroupMemberDoc> findByGroupIdAndUserIdIn(String groupId, List<String> userIds);
}
