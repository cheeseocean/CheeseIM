package com.cheeseocean.im.social.repository;

import com.cheeseocean.im.social.domain.UserSettingsDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserSettingsMongoRepository extends MongoRepository<UserSettingsDoc, String> {
}
