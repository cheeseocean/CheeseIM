package com.cheeseocean.im.common.core.business.mongo.repository;

import com.cheeseocean.im.common.core.business.mongo.document.GroupDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

/** group 集合的 Spring Data 访问接口 */
public interface GroupMongoRepository extends MongoRepository<GroupDoc, String> {
}
