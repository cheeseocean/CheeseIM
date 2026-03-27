package com.cheeseocean.im.social.service;

import com.cheeseocean.im.common.api.user.UserSettingsService;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.social.domain.UserSettingsDoc;
import com.cheeseocean.im.social.repository.UserSettingsMongoRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class UserSettingsServiceImpl implements UserSettingsService {

    private static final String FIELD_GLOBAL_RECV_MSG_OPT = "globalRecvMsgOpt";

    private final UserSettingsMongoRepository userSettingsMongoRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserSettingsNotifier userSettingsNotifier;

    public UserSettingsServiceImpl(UserSettingsMongoRepository userSettingsMongoRepository,
                                   RedisTemplate<String, Object> redisTemplate,
                                   UserSettingsNotifier userSettingsNotifier) {
        this.userSettingsMongoRepository = userSettingsMongoRepository;
        this.redisTemplate = redisTemplate;
        this.userSettingsNotifier = userSettingsNotifier;
    }

    @Override
    public int getGlobalRecvMsgOpt(String userId) {
        Object cached = redisTemplate.opsForHash().get(settingsKey(userId), FIELD_GLOBAL_RECV_MSG_OPT);
        if (cached != null) {
            return toInt(cached);
        }
        return userSettingsMongoRepository.findById(userId)
                .map(UserSettingsDoc::getGlobalRecvMsgOpt)
                .orElse(0);
    }

    @Override
    public void setGlobalRecvMsgOpt(String userId, int opt) {
        UserSettingsDoc doc = userSettingsMongoRepository.findById(userId)
                .orElseGet(() -> {
                    UserSettingsDoc d = new UserSettingsDoc();
                    d.setUserId(userId);
                    return d;
                });
        doc.setGlobalRecvMsgOpt(opt);
        userSettingsMongoRepository.save(doc);
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
