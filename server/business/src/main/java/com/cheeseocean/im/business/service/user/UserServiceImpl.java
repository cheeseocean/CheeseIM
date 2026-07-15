package com.cheeseocean.im.business.service.user;

import com.cheeseocean.im.common.api.business.domain.User;
import com.cheeseocean.im.common.api.dto.user.RegisterUserRequest;
import com.cheeseocean.im.common.api.dto.user.UpdateUserInfoRequest;
import com.cheeseocean.im.common.api.user.UserInfoService;
import com.cheeseocean.im.common.core.cache.CacheRegion;
import com.cheeseocean.im.common.core.cache.CacheStore;
import com.cheeseocean.im.common.core.business.repository.UserRepository;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cheeseocean.im.common.core.constants.RedisKeys.USER_INFO_PREFIX;
import static com.cheeseocean.im.common.core.constants.RedisKeys.USER_RECEIVE_OPTIONS_PREFIX;

/**
 * 用户基础信息服务实现。
 *
 * <p>服务层直接返回 {@link User} 领域对象，
 * 并通过类型化远端缓存维护用户资料和全局接收选项。
 */
@Service
@DubboService
public class UserServiceImpl implements UserInfoService {

    private static final Logger log = CommonLoggers.SOCIAL;
    private static final int NOTIFICATION_ACCOUNT_MIN_LEVEL = 2;
    private static final Duration USER_CACHE_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final CacheRegion<User> userInfoCache;
    private final CacheRegion<Integer> userReceiveOptCache;

    public UserServiceImpl(UserRepository userRepository, CacheStore cacheStore) {
        this.userRepository = userRepository;
        this.userInfoCache = cacheStore.region(USER_INFO_PREFIX, User.class, USER_CACHE_TTL);
        this.userReceiveOptCache = cacheStore.region(USER_RECEIVE_OPTIONS_PREFIX, Integer.class, USER_CACHE_TTL);
    }

    @Override
    public List<User> getUsersInfo(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> dedupedUserIds = userIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (dedupedUserIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, User> cached = userInfoCache.getAll(new LinkedHashSet<>(dedupedUserIds));
        Map<String, User> resolved = new LinkedHashMap<>();
        if (cached != null) {
            resolved.putAll(cached);
        }

        List<String> misses = dedupedUserIds.stream()
                .filter(userId -> !resolved.containsKey(userId))
                .collect(Collectors.toCollection(ArrayList::new));
        if (!misses.isEmpty()) {
            List<User> loaded = userRepository.findByIds(misses);
            Map<String, User> loadedMap = loaded.stream()
                    .collect(Collectors.toMap(
                            User::getUserId,
                            user -> user,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            if (!loadedMap.isEmpty()) {
                userInfoCache.putAll(loadedMap);
                resolved.putAll(loadedMap);
            }
        }

        return dedupedUserIds.stream()
                .map(resolved::get)
                .filter(user -> user != null)
                .map(this::copyUser)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public User getUserInfo(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        User cached = userInfoCache.getOrLoad(userId, () -> userRepository.findById(userId).orElse(null));
        return copyUser(cached);
    }

    @Override
    public List<User> pageQueryUsers(int pageNum, int pageSize, String keyword) {
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        return StringUtils.hasText(keyword)
                ? userRepository.pageByKeyword(keyword, pageSize, offset)
                : userRepository.pageAll(pageSize, offset);
    }

    @Override
    public long countUsers(String keyword) {
        return StringUtils.hasText(keyword)
                ? userRepository.countByKeyword(keyword)
                : userRepository.countAll();
    }

    @Override
    public List<String> getAllUserIds(int pageNum, int pageSize) {
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        return userRepository.findAllUserIds(pageSize, offset);
    }

    @Override
    public List<String> filterExistingUserIds(List<String> userIds) {
        return userRepository.findExistingUserIds(userIds);
    }

    @Override
    public void registerUsers(List<RegisterUserRequest> requests) {
        List<String> userIds = requests.stream()
                .map(RegisterUserRequest::getUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(ArrayList::new));
        Set<String> existing = new HashSet<>(userRepository.findExistingUserIds(userIds));
        if (!existing.isEmpty()) {
            throw new IllegalArgumentException("以下 userId 已注册：" + existing);
        }
        long now = System.currentTimeMillis();
        List<User> users = requests.stream()
                .map(req -> {
                    User user = new User();
                    user.setUserId(req.getUserId());
                    user.setNickname(req.getNickname());
                    user.setAvatarUrl(req.getFaceUrl());
                    user.setEx(req.getEx());
                    user.setAppManagerLevel(req.getAppManagerLevel());
                    user.setCreateTime(now);
                    return user;
                })
                .collect(Collectors.toCollection(ArrayList::new));
        userRepository.saveAll(users);
        evictUsersInfoCache(users.stream()
                .map(User::getUserId)
                .collect(Collectors.toCollection(ArrayList::new)));
        log.info("批量注册用户成功，数量={}", users.size());
    }

    @Override
    public void updateUserInfo(String userId, UpdateUserInfoRequest request) {
        Map<String, Object> fields = new HashMap<>();
        if (request.getNickname() != null) {
            fields.put("nickname", request.getNickname());
        }
        if (request.getFaceUrl() != null) {
            fields.put("avatarUrl", request.getFaceUrl());
        }
        if (request.getEx() != null) {
            fields.put("ex", request.getEx());
        }
        if (fields.isEmpty()) {
            return;
        }
        userRepository.updateFields(userId, fields);
        evictUserCaches(userId, false);
        log.info("更新用户信息成功，userId={}", userId);
    }

    @Override
    public String addNotificationAccount(String userId, String nickname, String faceUrl, int appManagerLevel) {
        if (appManagerLevel < NOTIFICATION_ACCOUNT_MIN_LEVEL) {
            throw new IllegalArgumentException("appManagerLevel 须 >= " + NOTIFICATION_ACCOUNT_MIN_LEVEL);
        }
        String actualUserId = StringUtils.hasText(userId) ? userId : generateUserId();
        if (userRepository.exists(actualUserId)) {
            throw new IllegalArgumentException("userId 已被占用：" + actualUserId);
        }
        User user = new User();
        user.setUserId(actualUserId);
        user.setNickname(nickname);
        user.setAvatarUrl(faceUrl);
        user.setAppManagerLevel(appManagerLevel);
        user.setCreateTime(System.currentTimeMillis());
        List<User> users = new ArrayList<>(1);
        users.add(user);
        userRepository.saveAll(users);
        List<String> userIds = new ArrayList<>(1);
        userIds.add(actualUserId);
        evictUsersInfoCache(userIds);
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
            fields.put("avatarUrl", faceUrl);
        }
        if (fields.isEmpty()) {
            return;
        }
        userRepository.updateFields(userId, fields);
        evictUserCaches(userId, false);
    }

    @Override
    public List<User> searchNotificationAccounts(String keyword, Integer appManagerLevel, int pageNum, int pageSize) {
        int offset = Math.max(0, (pageNum - 1) * pageSize);
        return userRepository.pageNotificationAccounts(keyword, appManagerLevel, pageSize, offset);
    }

    @Override
    public User getNotificationAccount(String userId) {
        User user = getUserInfo(userId);
        if (user == null || !user.isNotificationAccount()) {
            return null;
        }
        return user;
    }

    @Override
    public int getReceiveOptions(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        Integer opt = userReceiveOptCache.getOrLoad(userId, () -> userRepository.getGlobalReceiveOption(userId));
        return opt == null ? 0 : opt;
    }

    /**
     * 批量失效用户资料缓存。
     */
    private void evictUsersInfoCache(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userInfoCache.evictAll(new LinkedHashSet<>(userIds));
    }

    /**
     * 失效单用户资料和接收选项缓存。
     */
    private void evictUserCaches(String userId, boolean receiveOptChanged) {
        userInfoCache.evict(userId);
        if (receiveOptChanged) {
            userReceiveOptCache.evict(userId);
        }
    }

    /**
     * 返回防御性副本，避免外部代码修改缓存对象。
     */
    private User copyUser(User user) {
        if (user == null) {
            return null;
        }
        User copy = new User();
        copy.setUserId(user.getUserId());
        copy.setNickname(user.getNickname());
        copy.setAvatarUrl(user.getAvatarUrl());
        copy.setEx(user.getEx());
        copy.setAppManagerLevel(user.getAppManagerLevel());
        copy.setReceiveOpt(user.getReceiveOpt());
        copy.setCreateTime(user.getCreateTime());
        return copy;
    }

    /**
     * 生成随机 10 位纯数字 userId（首位非零）。
     */
    private String generateUserId() {
        StringBuilder sb = new StringBuilder();
        sb.append((char) ('1' + (int) (Math.random() * 9)));
        for (int i = 1; i < 10; i++) {
            sb.append((char) ('0' + (int) (Math.random() * 10)));
        }
        return sb.toString();
    }
}
