package com.cheeseocean.im.common.core.business.mongo.repository;

import com.cheeseocean.im.common.core.business.mongo.document.UserDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

/** User 集合的 Spring Data 访问接口 */
public interface UserMongoRepository extends MongoRepository<UserDoc, String> {
}
