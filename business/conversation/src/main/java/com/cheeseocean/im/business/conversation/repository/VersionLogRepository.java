package com.cheeseocean.im.business.conversation.repository;

import com.cheeseocean.im.business.conversation.entity.VersionLogMongo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 版本日志存储库
 * 
 * @author CheeseIM
 */
@Repository
public interface VersionLogRepository extends MongoRepository<VersionLogMongo, String> {
    
    /**
     * 根据用户ID查找最大版本
     */
    @Query(value = "{'user_id': ?0}", sort = "{'version': -1}")
    Optional<VersionLogMongo> findTopByUserIDOrderByVersionDesc(String userID);
    
    /**
     * 根据用户ID和版本查找版本日志
     */
    @Query(value = "{'user_id': ?0, 'version': {'$gte': ?1}}", sort = "{'version': 1}")
    List<VersionLogMongo> findByUserIDAndVersionGreaterThanEqual(String userID, Long version, Pageable pageable);
    
    /**
     * 根据用户ID查找所有版本日志
     */
    List<VersionLogMongo> findByUserIDOrderByVersionDesc(String userID);
    
    /**
     * 根据用户ID删除所有版本日志
     */
    void deleteByUserID(String userID);
}
