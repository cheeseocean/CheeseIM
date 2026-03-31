package com.cheeseocean.im.common.core.business.mongo.repository;

import com.cheeseocean.im.common.core.business.mongo.document.GroupApplicationDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/** group_request 集合的 Spring Data 访问接口 */
public interface GroupApplicationMongoRepository extends MongoRepository<GroupApplicationDoc, String> {

    Optional<GroupApplicationDoc> findByUserIdAndGroupId(String userId, String groupId);

    List<GroupApplicationDoc> findByGroupIdAndHandleResultOrderByReqTimeDesc(String groupId, int handleResult);

    List<GroupApplicationDoc> findByUserIdAndHandleResultOrderByReqTimeDesc(String userId, int handleResult);
}
