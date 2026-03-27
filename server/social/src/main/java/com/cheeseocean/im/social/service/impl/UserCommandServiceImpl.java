package com.cheeseocean.im.social.service.impl;

import com.cheeseocean.im.common.api.dto.user.UserCommandDTO;
import com.cheeseocean.im.common.api.user.UserCommandService;
import com.cheeseocean.im.social.domain.UserCommandDocument;
import com.cheeseocean.im.social.repository.UserCommandMongoRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户自定义命令服务实现。
 *
 * <p>以确定性 _id "{userId}:{type}:{uuid}" 实现幂等 upsert，
 * 保证 add 操作可安全重试。
 */
@Service
@DubboService
public class UserCommandServiceImpl implements UserCommandService {

    private final UserCommandMongoRepository commandRepository;
    private final MongoTemplate mongoTemplate;

    public UserCommandServiceImpl(UserCommandMongoRepository commandRepository,
                                  MongoTemplate mongoTemplate) {
        this.commandRepository = commandRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void addUserCommand(String userId, int type, String uuid, String value, String ex) {
        String id    = docId(userId, type, uuid);
        Query  query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .setOnInsert("_id",        id)
                .setOnInsert("userId",     userId)
                .setOnInsert("type",       type)
                .setOnInsert("uuid",       uuid)
                .setOnInsert("createTime", Instant.now())
                .set("value", value)
                .set("ex",    ex != null ? ex : "");
        mongoTemplate.upsert(query, update, UserCommandDocument.class);
    }

    @Override
    public void deleteUserCommand(String userId, int type, String uuid) {
        commandRepository.deleteByUserIdAndTypeAndUuid(userId, type, uuid);
    }

    @Override
    public void updateUserCommand(String userId, int type, String uuid, String value, String ex) {
        String id    = docId(userId, type, uuid);
        Query  query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update();
        if (value != null) {
            update.set("value", value);
        }
        if (ex != null) {
            update.set("ex", ex);
        }
        mongoTemplate.updateFirst(query, update, UserCommandDocument.class);
    }

    @Override
    public List<UserCommandDTO> getUserCommands(String userId, int type) {
        return commandRepository.findByUserIdAndType(userId, type)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<UserCommandDTO> getAllUserCommands(String userId) {
        return commandRepository.findByUserId(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    /** 文档主键：userId:type:uuid */
    private static String docId(String userId, int type, String uuid) {
        return userId + ":" + type + ":" + uuid;
    }

    /** 将 MongoDB 文档映射为 DTO。 */
    private UserCommandDTO toDTO(UserCommandDocument doc) {
        UserCommandDTO dto = new UserCommandDTO();
        dto.setType(doc.getType());
        dto.setUuid(doc.getUuid());
        dto.setValue(doc.getValue());
        dto.setEx(doc.getEx());
        dto.setCreateTime(doc.getCreateTime() != null ? doc.getCreateTime().toEpochMilli() : 0L);
        return dto;
    }
}
