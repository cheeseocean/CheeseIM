package com.cheeseocean.im.social.repository;

import com.cheeseocean.im.social.domain.UserDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 用户基础信息 MongoDB 仓储。
 */
public interface UserMongoRepository extends MongoRepository<UserDocument, String> {

    /**
     * 按昵称模糊查询用户列表。
     */
    List<UserDocument> findByNicknameContaining(String nickname);

    /**
     * 查询管理员级别大于等于指定值的用户列表。
     * 用于搜索通知/系统账号（appManagerLevel >= 2）。
     */
    List<UserDocument> findByAppManagerLevelGreaterThanEqual(int minLevel);

    /**
     * 查询管理员级别等于指定值的用户列表。
     */
    List<UserDocument> findByAppManagerLevel(int level);
}
