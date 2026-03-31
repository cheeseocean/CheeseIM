package com.cheeseocean.im.business.service.user;

import com.cheeseocean.im.common.api.dto.user.RegisterUserRequest;
import com.cheeseocean.im.common.api.dto.user.UpdateUserInfoRequest;
import com.cheeseocean.im.common.api.dto.user.UserInfoDTO;
import com.cheeseocean.im.common.api.user.UserInfoService;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.common.core.business.domain.User;
import com.cheeseocean.im.common.core.business.repository.UserRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户基础信息服务实现。
 *
 * <p>通过 {@link UserRepository} 完成持久化，不直接依赖 MongoDB 实现类。
 * 注册、更新、查询均委托仓储处理，保持服务层专注于业务流程编排。
 */
@Service
@DubboService
public class UserServiceImpl implements UserInfoService {

    private static final Logger log = CommonLoggers.SOCIAL;

    /** 通知账号最低管理员级别 */
    private static final int NOTIFICATION_ACCOUNT_MIN_LEVEL = 2;

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ── 查询 ──────────────────────────────────────────────────────────────────

    @Override
    public List<UserInfoDTO> getUsersInfo(List<String> userIds) {
        return userRepository.findByIds(userIds).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public UserInfoDTO getUserInfo(String userId) {
        return userRepository.findById(userId).map(this::toDTO).orElse(null);
    }

    @Override
    public List<UserInfoDTO> pageQueryUsers(int pageNum, int pageSize, String keyword) {
        return userRepository.queryUsers(keyword, pageNum, pageSize).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public long countUsers(String keyword) {
        return userRepository.countUsers(keyword);
    }

    @Override
    public List<String> getAllUserIds(int pageNum, int pageSize) {
        return userRepository.findAllUserIds(pageNum, pageSize);
    }

    @Override
    public List<String> filterExistingUserIds(List<String> userIds) {
        return userRepository.filterExistingIds(userIds);
    }

    // ── 注册与更新 ────────────────────────────────────────────────────────────

    @Override
    public void registerUsers(List<RegisterUserRequest> requests) {
        List<String> userIds = requests.stream()
                .map(RegisterUserRequest::getUserId).collect(Collectors.toList());
        // 检查是否已有重复注册
        Set<String> existing = userRepository.filterExistingIds(userIds).stream()
                .collect(Collectors.toSet());
        if (!existing.isEmpty()) {
            throw new IllegalArgumentException("以下 userId 已注册：" + existing);
        }
        long now = System.currentTimeMillis();
        List<User> users = requests.stream().map(req -> {
            User user = new User();
            user.setUserId(req.getUserId());
            user.setNickname(req.getNickname());
            user.setFaceUrl(req.getFaceUrl());
            user.setEx(req.getEx());
            user.setAppManagerLevel(req.getAppManagerLevel());
            user.setCreateTime(now);
            return user;
        }).collect(Collectors.toList());
        userRepository.saveAll(users);
        log.info("批量注册用户成功，数量={}", users.size());
    }

    @Override
    public void updateUserInfo(String userId, UpdateUserInfoRequest request) {
        Map<String, Object> fields = new HashMap<>();
        if (request.getNickname() != null) {
            fields.put("nickname", request.getNickname());
        }
        if (request.getFaceUrl() != null) {
            fields.put("faceUrl", request.getFaceUrl());
        }
        if (request.getEx() != null) {
            fields.put("ex", request.getEx());
        }
        if (fields.isEmpty()) {
            return;
        }
        userRepository.updateFields(userId, fields);
        log.info("更新用户信息成功，userId={}", userId);
    }

    // ── 通知系统账号管理 ──────────────────────────────────────────────────────

    @Override
    public String addNotificationAccount(String userId, String nickname, String faceUrl, int appManagerLevel) {
        if (appManagerLevel < NOTIFICATION_ACCOUNT_MIN_LEVEL) {
            throw new IllegalArgumentException("appManagerLevel 须 >= " + NOTIFICATION_ACCOUNT_MIN_LEVEL);
        }
        String actualUserId = StringUtils.hasText(userId) ? userId : generateUserId();
        if (userRepository.existsById(actualUserId)) {
            throw new IllegalArgumentException("userId 已被占用：" + actualUserId);
        }
        User user = new User();
        user.setUserId(actualUserId);
        user.setNickname(nickname);
        user.setFaceUrl(faceUrl);
        user.setAppManagerLevel(appManagerLevel);
        user.setCreateTime(System.currentTimeMillis());
        userRepository.save(user);
        log.info("注册通知账号成功，userId={}，level={}", actualUserId, appManagerLevel);
        return actualUserId;
    }

    @Override
    public void updateNotificationAccount(String userId, String nickname, String faceUrl) {
        Map<String, Object> fields = new HashMap<>();
        if (StringUtils.hasText(nickname)) {
            fields.put("nickname", nickname);
        }
        if (StringUtils.hasText(faceUrl)) {
            fields.put("faceUrl", faceUrl);
        }
        if (!fields.isEmpty()) {
            userRepository.updateFields(userId, fields);
        }
    }

    @Override
    public List<UserInfoDTO> searchNotificationAccounts(String keyword, Integer appManagerLevel,
                                                        int pageNum, int pageSize) {
        return userRepository.queryNotificationAccounts(keyword, appManagerLevel, pageNum, pageSize)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public UserInfoDTO getNotificationAccount(String userId) {
        return userRepository.findById(userId)
                .filter(User::isNotificationAccount)
                .map(this::toDTO)
                .orElse(null);
    }

    // ── 私有工具方法 ──────────────────────────────────────────────────────────

    /** 将领域对象转换为 DTO。 */
    private UserInfoDTO toDTO(User user) {
        UserInfoDTO dto = new UserInfoDTO();
        dto.setUserId(user.getUserId());
        dto.setNickname(user.getNickname());
        dto.setFaceUrl(user.getFaceUrl());
        dto.setEx(user.getEx());
        dto.setAppManagerLevel(user.getAppManagerLevel());
        dto.setCreateTime(user.getCreateTime());
        return dto;
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
