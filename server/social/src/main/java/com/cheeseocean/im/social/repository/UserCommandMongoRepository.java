package com.cheeseocean.im.social.repository;

import com.cheeseocean.im.social.domain.UserCommandDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * 用户自定义命令 MongoDB 仓储。
 */
public interface UserCommandMongoRepository extends MongoRepository<UserCommandDocument, String> {

    /**
     * 查询指定用户、指定类型的所有命令。
     */
    List<UserCommandDocument> findByUserIdAndType(String userId, int type);

    /**
     * 查询指定用户的所有命令（所有类型）。
     */
    List<UserCommandDocument> findByUserId(String userId);

    /**
     * 精确查询指定用户的某条命令。
     */
    Optional<UserCommandDocument> findByUserIdAndTypeAndUuid(String userId, int type, String uuid);

    /**
     * 删除指定用户的某条命令。
     */
    void deleteByUserIdAndTypeAndUuid(String userId, int type, String uuid);
}
