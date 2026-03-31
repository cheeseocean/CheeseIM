package com.cheeseocean.im.common.core.business.mongo.repository;

import com.cheeseocean.im.common.core.business.mongo.document.group.GroupRequestDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/** group_request 集合的 Spring Data 访问接口 */
public interface GroupApplicationMongoRepository extends MongoRepository<GroupRequestDoc, String> {

    Optional<GroupRequestDoc> findByUserIdAndGroupId(String userId, String groupId);

    List<GroupRequestDoc> findByGroupIdAndHandleResultOrderByReqTimeDesc(String groupId, int handleResult);

    List<GroupRequestDoc> findByUserIdAndHandleResultOrderByReqTimeDesc(String userId, int handleResult);
}
