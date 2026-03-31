package com.cheeseocean.im.business.service.user;

import com.cheeseocean.im.common.api.user.UserSettingsService;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.business.repository.UserRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 用户全局设置服务实现。
 *
 * <p>globalRecvMsgOpt 合并存储在 {@code user} 集合，与用户基础信息共用一张文档。
 * 读路径：Redis Hash → UserRepository.findById（字段级精准读取由仓储实现决定）。
 * 写路径：UserRepository.updateFields → 更新 Redis Hash → 发布变更事件。
 */
@Service
@DubboService
public class UserSettingsServiceImpl implements UserSettingsService {

    private static final String FIELD_GLOBAL_RECV_MSG_OPT = "globalRecvMsgOpt";

    private final UserRepository                userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserSettingsNotifier          userSettingsNotifier;

    public UserSettingsServiceImpl(UserRepository userRepository,
                                   org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate,
                                   UserSettingsNotifier userSettingsNotifier) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.userSettingsNotifier = userSettingsNotifier;
    }

    @Override
    public int getGlobalRecvMsgOpt(String userId) {
        Object cached = redisTemplate.opsForHash().get(settingsKey(userId), FIELD_GLOBAL_RECV_MSG_OPT);
        if (cached != null) {
            return toInt(cached);
        }
        return userRepository.findById(userId)
                .map(u -> u.getGlobalRecvMsgOpt())
                .orElse(0);
    }

    @Override
    public void setGlobalRecvMsgOpt(String userId, int opt) {
        userRepository.updateFields(userId, Map.of(FIELD_GLOBAL_RECV_MSG_OPT, opt));
        redisTemplate.opsForHash().put(settingsKey(userId), FIELD_GLOBAL_RECV_MSG_OPT, opt);
        userSettingsNotifier.notifyGlobalRecvMsgOptChanged(userId, opt);
    }

    private String settingsKey(String userId) {
        return RedisKeys.userSettings(userId);
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
