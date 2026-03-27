package com.cheeseocean.im.social.service.impl;

import com.cheeseocean.im.common.api.dto.user.RegisterUserRequest;
import com.cheeseocean.im.common.api.dto.user.UpdateUserInfoRequest;
import com.cheeseocean.im.common.api.dto.user.UserInfoDTO;
import com.cheeseocean.im.common.api.user.UserInfoService;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.social.domain.UserDocument;
import com.cheeseocean.im.social.repository.UserMongoRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户基础信息服务实现。
 *
 * <p>读路径：直接查 MongoDB（可在此处叠加 Redis 缓存层，当前版本省略）。
 * 写路径：写 MongoDB，若后续引入缓存则在写后失效对应 key。
 */
@Service
@DubboService
public class UserServiceImpl implements UserInfoService {

    private static final Logger log = CommonLoggers.SOCIAL;

    /** 通知账号最低管理员级别 */
    private static final int NOTIFICATION_ACCOUNT_MIN_LEVEL = 2;

    private final UserMongoRepository userMongoRepository;
    private final MongoTemplate mongoTemplate;

    public UserServiceImpl(UserMongoRepository userMongoRepository, MongoTemplate mongoTemplate) {
        this.userMongoRepository = userMongoRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // ── 查询 ──────────────────────────────────────────────────────────────────

    @Override
    public List<UserInfoDTO> getUsersInfo(List<String> userIds) {
        List<UserDocument> docs = userMongoRepository.findAllById(userIds);
        // 按入参顺序返回（不存在的跳过）
        Map<String, UserDocument> docMap = docs.stream()
                .collect(Collectors.toMap(UserDocument::getUserId, d -> d));
        List<UserInfoDTO> result = new ArrayList<>();
        for (String uid : userIds) {
            if (docMap.containsKey(uid)) {
                result.add(toDTO(docMap.get(uid)));
            }
        }
        return result;
    }

    @Override
    public UserInfoDTO getUserInfo(String userId) {
        return userMongoRepository.findById(userId).map(this::toDTO).orElse(null);
    }

    @Override
    public List<UserInfoDTO> pageQueryUsers(int pageNum, int pageSize, String keyword) {
        Query query = buildUserQuery(keyword);
        query.with(PageRequest.of(pageNum - 1, pageSize, Sort.by("createTime").descending()));
        return mongoTemplate.find(query, UserDocument.class).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public long countUsers(String keyword) {
        return mongoTemplate.count(buildUserQuery(keyword), UserDocument.class);
    }

    @Override
    public List<String> getAllUserIds(int pageNum, int pageSize) {
        Query query = new Query()
                .with(PageRequest.of(pageNum - 1, pageSize))
                .with(Sort.by("createTime").ascending());
        query.fields().include("userId");
        return mongoTemplate.find(query, UserDocument.class).stream()
                .map(UserDocument::getUserId).collect(Collectors.toList());
    }

    @Override
    public List<String> filterExistingUserIds(List<String> userIds) {
        List<UserDocument> docs = userMongoRepository.findAllById(userIds);
        return docs.stream().map(UserDocument::getUserId).collect(Collectors.toList());
    }

    // ── 注册与更新 ────────────────────────────────────────────────────────────

    @Override
    public void registerUsers(List<RegisterUserRequest> requests) {
        List<String> userIds = requests.stream()
                .map(RegisterUserRequest::getUserId).collect(Collectors.toList());
        // 检查是否已有重复注册
        Set<String> existing = userMongoRepository.findAllById(userIds).stream()
                .map(UserDocument::getUserId).collect(Collectors.toSet());
        if (!existing.isEmpty()) {
            throw new IllegalArgumentException("以下 userId 已注册：" + existing);
        }

        Instant now = Instant.now();
        List<UserDocument> docs = requests.stream().map(req -> {
            UserDocument doc = new UserDocument();
            doc.setUserId(req.getUserId());
            doc.setNickname(req.getNickname());
            doc.setFaceUrl(req.getFaceUrl());
            doc.setEx(req.getEx());
            doc.setAppManagerLevel(req.getAppManagerLevel());
            doc.setCreateTime(now);
            return doc;
        }).collect(Collectors.toList());

        userMongoRepository.saveAll(docs);
        log.info("批量注册用户成功，数量={}", docs.size());
    }

    @Override
    public void updateUserInfo(String userId, UpdateUserInfoRequest request) {
        Query  query  = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update();
        if (request.getNickname() != null) {
            update.set("nickname", request.getNickname());
        }
        if (request.getFaceUrl() != null) {
            update.set("faceUrl", request.getFaceUrl());
        }
        if (request.getEx() != null) {
            update.set("ex", request.getEx());
        }
        if (update.getUpdateObject().isEmpty()) {
            return;
        }
        mongoTemplate.updateFirst(query, update, UserDocument.class);
        log.info("更新用户信息成功，userId={}", userId);
    }

    // ── 通知系统账号管理 ──────────────────────────────────────────────────────

    @Override
    public String addNotificationAccount(String userId, String nickname, String faceUrl, int appManagerLevel) {
        if (appManagerLevel < NOTIFICATION_ACCOUNT_MIN_LEVEL) {
            throw new IllegalArgumentException("appManagerLevel 须 >= " + NOTIFICATION_ACCOUNT_MIN_LEVEL);
        }
        String actualUserId = StringUtils.hasText(userId) ? userId : generateUserId();
        if (userMongoRepository.existsById(actualUserId)) {
            throw new IllegalArgumentException("userId 已被占用：" + actualUserId);
        }
        UserDocument doc = new UserDocument();
        doc.setUserId(actualUserId);
        doc.setNickname(nickname);
        doc.setFaceUrl(faceUrl);
        doc.setAppManagerLevel(appManagerLevel);
        doc.setCreateTime(Instant.now());
        userMongoRepository.save(doc);
        log.info("注册通知账号成功，userId={}，level={}", actualUserId, appManagerLevel);
        return actualUserId;
    }

    @Override
    public void updateNotificationAccount(String userId, String nickname, String faceUrl) {
        Query  query  = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update();
        if (StringUtils.hasText(nickname)) {
            update.set("nickname", nickname);
        }
        if (StringUtils.hasText(faceUrl)) {
            update.set("faceUrl", faceUrl);
        }
        mongoTemplate.updateFirst(query, update, UserDocument.class);
    }

    @Override
    public List<UserInfoDTO> searchNotificationAccounts(String keyword, Integer appManagerLevel,
                                                        int pageNum, int pageSize) {
        Criteria criteria = Criteria.where("appManagerLevel").gte(NOTIFICATION_ACCOUNT_MIN_LEVEL);
        if (appManagerLevel != null) {
            criteria = Criteria.where("appManagerLevel").is(appManagerLevel);
        }
        if (StringUtils.hasText(keyword)) {
            criteria = new Criteria().andOperator(
                    criteria,
                    new Criteria().orOperator(
                            Criteria.where("_id").is(keyword),
                            Criteria.where("nickname").regex(keyword, "i")
                    )
            );
        }
        Query query = Query.query(criteria)
                .with(PageRequest.of(pageNum - 1, pageSize));
        return mongoTemplate.find(query, UserDocument.class).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public UserInfoDTO getNotificationAccount(String userId) {
        return userMongoRepository.findById(userId)
                .filter(doc -> doc.getAppManagerLevel() >= NOTIFICATION_ACCOUNT_MIN_LEVEL)
                .map(this::toDTO)
                .orElse(null);
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    /** 将 MongoDB 文档转换为 DTO。 */
    private UserInfoDTO toDTO(UserDocument doc) {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setUserId(doc.getUserId());
        dto.setNickname(doc.getNickname());
        dto.setFaceUrl(doc.getFaceUrl());
        dto.setEx(doc.getEx());
        dto.setAppManagerLevel(doc.getAppManagerLevel());
        dto.setCreateTime(doc.getCreateTime() != null ? doc.getCreateTime().toEpochMilli() : 0L);
        return dto;
    }

    /**
     * 构建用户列表查询条件。
     * keyword 非空时对普通用户按 userId 精确或 nickname 模糊匹配。
     */
    private Query buildUserQuery(String keyword) {
        // 只查普通用户（appManagerLevel < NOTIFICATION_ACCOUNT_MIN_LEVEL）
        Criteria base = Criteria.where("appManagerLevel").lt(NOTIFICATION_ACCOUNT_MIN_LEVEL);
        if (!StringUtils.hasText(keyword)) {
            return Query.query(base);
        }
        Criteria search = new Criteria().orOperator(
                Criteria.where("_id").is(keyword),
                Criteria.where("nickname").regex(keyword, "i")
        );
        return Query.query(new Criteria().andOperator(base, search));
    }

    /** 生成随机 10 位纯数字 userId（首位非零）。 */
    private String generateUserId() {
        StringBuilder sb = new StringBuilder();
        sb.append((char) ('1' + (int) (Math.random() * 9)));
        for (int i = 1; i < 10; i++) {
            sb.append((char) ('0' + (int) (Math.random() * 10)));
        }
        return sb.toString();
    }
}
