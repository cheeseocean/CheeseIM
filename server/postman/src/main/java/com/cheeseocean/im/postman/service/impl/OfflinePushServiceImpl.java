package com.cheeseocean.im.postman.service.impl;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.core.constants.MessageDisplayConstants;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.postman.entity.OfflinePushConfig;
import com.cheeseocean.im.postman.entity.OfflinePushResult;
import com.cheeseocean.im.postman.entity.PushMessage;
import com.cheeseocean.im.postman.service.OfflinePushService;
import com.cheeseocean.im.postman.state.PushStateStore;
import com.cheeseocean.im.postman.provider.PushProvider;
import com.cheeseocean.im.common.core.metrics.ImMetrics;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * 离线推送服务实现
 * 通过第三方推送服务进行离线推送
 * 
 * @author xxxcrel
 */
@Service
public class OfflinePushServiceImpl implements OfflinePushService {
    
    private static final Logger logger = CommonLoggers.POSTMAN;
    
    private final List<PushProvider> pushProviders;
    private final DeviceTokenServiceImpl deviceTokenService;
    private final StringRedisTemplate redisTemplate;
    private final PushStateStore pushStateStore;
    private final Executor offlinePushExecutor;
    private final int submissionBatchSize;
    private final long submissionBatchTimeoutMillis;

    public OfflinePushServiceImpl(List<PushProvider> pushProviders,
                                  DeviceTokenServiceImpl deviceTokenService,
                                  StringRedisTemplate redisTemplate,
                                  PushStateStore pushStateStore,
                                  @Qualifier("offlinePushExecutor") Executor offlinePushExecutor,
                                  @Value("${cheeseim.push.executor.batch-size:500}") int submissionBatchSize,
                                  @Value("${cheeseim.push.executor.batch-timeout-ms:30000}") long submissionBatchTimeoutMillis) {
        this.pushProviders = pushProviders;
        this.deviceTokenService = deviceTokenService;
        this.redisTemplate = redisTemplate;
        this.pushStateStore = pushStateStore;
        this.offlinePushExecutor = offlinePushExecutor;
        this.submissionBatchSize = Math.max(1, submissionBatchSize);
        this.submissionBatchTimeoutMillis = Math.max(100L, submissionBatchTimeoutMillis);
    }
    
    /**
     * 用户离线推送配置缓存Key前缀
     */
    private static final String OFFLINE_PUSH_CONFIG_KEY_PREFIX = "cheese_im:offline_push_config:";
    
    /**
     * 缓存过期时间（小时）
     */
    private static final long CACHE_EXPIRE_HOURS = 24;
    
    @Override
    public OfflinePushResult pushMessageToUsers(Message message, List<String> targetUsers) {
        try {
            if (message == null || targetUsers == null || targetUsers.isEmpty()) {
                return OfflinePushResult.failure("参数无效");
            }
            
            long startTime = System.currentTimeMillis();
            
            logger.info("开始离线推送: messageID={}, targetUsers={}", 
                       message.getServerMsgId(), targetUsers.size());
            
            Map<String, UserPushOutcome> outcomes = new ConcurrentHashMap<>();
            
            // 分批提交并等待，限制在途 future 数量；线程池队列满时明确记录过载失败。
            for (int batchStart = 0; batchStart < targetUsers.size(); batchStart += submissionBatchSize) {
                int batchEnd = Math.min(targetUsers.size(), batchStart + submissionBatchSize);
                Map<CompletableFuture<Void>, String> futures = new LinkedHashMap<>(batchEnd - batchStart);
                for (String userID : targetUsers.subList(batchStart, batchEnd)) {
                    try {
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    Map<String, String> userProviderResults = new HashMap<>();
                    try {
                        // 检查用户是否启用离线推送
                        if (!isOfflinePushEnabled(userID)) {
                            outcomes.putIfAbsent(userID, UserPushOutcome.failure(
                                    "用户已禁用离线推送", userProviderResults));
                            return;
                        }
                        
                        // 获取用户离线推送配置
                        OfflinePushConfig config = getUserOfflinePushConfig(userID);
                        
                        // 检查是否在免打扰时间
                        if (config.isInQuietTime() && !config.isAllowDuringQuietTime()) {
                            outcomes.putIfAbsent(userID, UserPushOutcome.failure(
                                    "当前在免打扰时间", userProviderResults));
                            return;
                        }
                        
                        // 生成推送内容
                        String title = generatePushTitle(message);
                        String content = generatePushContent(message);
                        
                        // 为用户创建推送消息
                        List<PushMessage> pushMessages = createPushMessagesForUser(
                                userID, title, content, message);
                        
                        if (pushMessages.isEmpty()) {
                            outcomes.putIfAbsent(userID, UserPushOutcome.failure(
                                    "无可用的推送消息", userProviderResults));
                            return;
                        }

                        // 先原子预占配额，避免多个 postman 副本同时越过每日上限。
                        int maxDailyCount = config.getMaxDailyCount() == null ? 0 : config.getMaxDailyCount();
                        if (!pushStateStore.claimDailyQuota(userID, maxDailyCount)) {
                            outcomes.putIfAbsent(userID, UserPushOutcome.failure(
                                    "已达到每日推送上限", userProviderResults));
                            return;
                        }
                        
                        // 发送推送消息
                        boolean userPushSuccess = false;
                        StringBuilder userErrorBuilder = new StringBuilder();
                        try {
                            for (PushMessage pushMessage : pushMessages) {
                                // 选择推送提供商
                                PushProvider provider = selectPushProvider(pushMessage.getPlatformType());
                                if (provider == null) {
                                    userErrorBuilder.append("平台").append(pushMessage.getPlatformType().getDisplayName())
                                            .append("无可用推送提供商; ");
                                    continue;
                                }

                                // 发送推送
                                long providerStarted = ImMetrics.startTimer();
                                PushProvider.PushResult pushResult;
                                try {
                                    pushResult = provider.sendPush(pushMessage);
                                    ImMetrics.offlinePush(provider.getProviderName(),
                                            pushResult != null && pushResult.isSuccess() ? "success" : "failure",
                                            providerStarted);
                                } catch (RuntimeException exception) {
                                    ImMetrics.offlinePush(provider.getProviderName(), "error", providerStarted);
                                    throw exception;
                                }

                                userProviderResults.put(userID + "_" + provider.getProviderName(),
                                        pushResult.isSuccess() ? "成功" : pushResult.getErrorMessage());

                                if (pushResult.isSuccess()) {
                                    userPushSuccess = true;
                                    break; // 任意一个平台推送成功即可
                                }
                                userErrorBuilder.append(provider.getProviderName())
                                        .append(": ").append(pushResult.getErrorMessage()).append("; ");
                            }

                            if (userPushSuccess) {
                                outcomes.putIfAbsent(userID, UserPushOutcome.success(userProviderResults));
                            } else {
                                outcomes.putIfAbsent(userID, UserPushOutcome.failure(
                                        userErrorBuilder.toString(), userProviderResults));
                            }
                        } finally {
                            if (!userPushSuccess) {
                                pushStateStore.releaseDailyQuota(userID);
                            }
                        }
                        
                    } catch (Exception e) {
                        logger.error("用户离线推送异常: userID={}", userID, e);
                        outcomes.putIfAbsent(userID, UserPushOutcome.failure(
                                "推送异常: " + e.getMessage(), userProviderResults));
                    }
                        }, offlinePushExecutor);
                        futures.put(future, userID);
                    } catch (RejectedExecutionException exception) {
                        logger.warn("离线推送线程池过载，拒绝任务: userID={}", userID);
                        outcomes.putIfAbsent(userID, UserPushOutcome.failure("离线推送服务繁忙", Map.of()));
                    }
                }
                awaitBatch(futures, outcomes);
            }
            
            long totalResponseTime = System.currentTimeMillis() - startTime;
            
            // 构造结果
            Map<String, UserPushOutcome> frozenOutcomes = Map.copyOf(outcomes);
            List<String> successUsers = frozenOutcomes.entrySet().stream()
                    .filter(entry -> entry.getValue().success())
                    .map(Map.Entry::getKey).toList();
            List<String> failedUsers = frozenOutcomes.entrySet().stream()
                    .filter(entry -> !entry.getValue().success())
                    .map(Map.Entry::getKey).toList();
            Map<String, String> userErrors = new HashMap<>();
            Map<String, String> providerResults = new HashMap<>();
            frozenOutcomes.forEach((userId, outcome) -> {
                if (!outcome.success()) userErrors.put(userId, outcome.error());
                providerResults.putAll(outcome.providerResults());
            });
            OfflinePushResult result;
            if (successUsers.isEmpty() && failedUsers.isEmpty()) {
                result = OfflinePushResult.failure("没有用户进行推送");
            } else if (failedUsers.isEmpty()) {
                result = OfflinePushResult.success(successUsers);
            } else {
                result = OfflinePushResult.partial(
                        successUsers, failedUsers, userErrors);
            }
            
            result.setProviderResults(providerResults);
            result.setTotalResponseTime(totalResponseTime);
            
            logger.info("离线推送完成: messageID={}, targetUsers={}, successCount={}, failedCount={}, totalTime={}ms", 
                       message.getServerMsgId(), targetUsers.size(),
                       successUsers.size(), failedUsers.size(), totalResponseTime);
            
            return result;
            
        } catch (Exception e) {
            logger.error("离线推送异常: messageID={}, targetUsers={}", 
                        message.getServerMsgId(), targetUsers.size(), e);
            return OfflinePushResult.failure("离线推送异常: " + e.getMessage());
        }
    }

    private void awaitBatch(Map<CompletableFuture<Void>, String> futures,
                            Map<String, UserPushOutcome> outcomes) {
        if (futures.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(futures.keySet().toArray(new CompletableFuture[0]))
                    .get(submissionBatchTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            long unfinished = futures.keySet().stream().filter(future -> !future.isDone()).count();
            futures.forEach((future, userId) -> {
                if (!future.isDone()) {
                    future.cancel(true);
                    outcomes.putIfAbsent(userId, UserPushOutcome.failure("离线推送执行超时", Map.of()));
                }
            });
            logger.error("离线推送批次执行超时: timeoutMs={}, unfinished={}",
                    submissionBatchTimeoutMillis, unfinished);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待离线推送批次时被中断", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("离线推送批次执行失败", exception.getCause());
        }
    }

    private record UserPushOutcome(boolean success, String error, Map<String, String> providerResults) {
        private UserPushOutcome {
            providerResults = Map.copyOf(providerResults);
        }

        static UserPushOutcome success(Map<String, String> providerResults) {
            return new UserPushOutcome(true, null, providerResults);
        }

        static UserPushOutcome failure(String error, Map<String, String> providerResults) {
            return new UserPushOutcome(false, error, providerResults);
        }
    }
    
    @Override
    public OfflinePushResult pushMessageToUser(Message message, String userID) {
        List<String> targetUsers = new ArrayList<>();
        targetUsers.add(userID);
        return pushMessageToUsers(message, targetUsers);
    }
    
    @Override
    public boolean isOfflinePushEnabled(String userID) {
        try {
            OfflinePushConfig config = getUserOfflinePushConfig(userID);
            return config.isEnabled();
        } catch (Exception e) {
            logger.error("检查用户离线推送状态失败: userID={}", userID, e);
            return true; // 默认启用
        }
    }
    
    @Override
    public OfflinePushConfig getUserOfflinePushConfig(String userID) {
        try {
            String configKey = OFFLINE_PUSH_CONFIG_KEY_PREFIX + userID;
            
            // 从缓存获取配置
            Map<Object, Object> configMap = redisTemplate.opsForHash().entries(configKey);
            
            if (configMap != null && !configMap.isEmpty()) {
                OfflinePushConfig config = mapToOfflinePushConfig(configMap);
                config.setCurrentDailyCount(pushStateStore.getDailyPushCount(userID));
                return config;
            }
            
            // 缓存未命中，创建默认配置
            OfflinePushConfig config = new OfflinePushConfig(userID);
            config.setCurrentDailyCount(pushStateStore.getDailyPushCount(userID));
            
            // 缓存配置
            Map<String, String> configData = offlinePushConfigToMap(config);
            redisTemplate.opsForHash().putAll(configKey, configData);
            redisTemplate.expire(configKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            
            return config;
            
        } catch (Exception e) {
            logger.error("获取用户离线推送配置失败: userID={}", userID, e);
            return new OfflinePushConfig(userID);
        }
    }
    
    @Override
    public boolean updateUserOfflinePushConfig(String userID, OfflinePushConfig config) {
        try {
            String configKey = OFFLINE_PUSH_CONFIG_KEY_PREFIX + userID;
            
            config.setLastUpdateTime(System.currentTimeMillis());
            
            Map<String, String> configData = offlinePushConfigToMap(config);
            redisTemplate.opsForHash().putAll(configKey, configData);
            redisTemplate.expire(configKey, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            
            logger.debug("用户离线推送配置已更新: userID={}", userID);
            return true;
            
        } catch (Exception e) {
            logger.error("更新用户离线推送配置失败: userID={}", userID, e);
            return false;
        }
    }
    
    /**
     * 选择推送提供商
     */
    private PushProvider selectPushProvider(PlatformType platformType) {
        if (pushProviders == null || pushProviders.isEmpty()) {
            return null;
        }
        
        // 优先选择支持指定平台且可用的提供商
        for (PushProvider provider : pushProviders) {
            if (provider.supportsPlatform(platformType) && provider.isAvailable()) {
                return provider;
            }
        }
        
        return null;
    }
    
    /**
     * 生成推送标题
     */
    private String generatePushTitle(Message message) {
        if (isNotificationMessage(message)) {
            return MessageDisplayConstants.PUSH_TITLE_SYSTEM_NOTIFICATION;
        }
        ChatType chatType = message.getChatType() == null ? ChatType.PRIVATE : message.getChatType();
        if (chatType == ChatType.PRIVATE) {
            // 单聊
            return message.getSenderNickName() != null ? message.getSenderNickName() : MessageDisplayConstants.PUSH_TITLE_NEW_MESSAGE;
        } else if (chatType == ChatType.GROUP) {
            // 群聊
            return MessageDisplayConstants.PUSH_TITLE_GROUP_MESSAGE;
        } else {
            return MessageDisplayConstants.PUSH_TITLE_SYSTEM_NOTIFICATION;
        }
    }
    
    /**
     * 生成推送内容
     */
    private String generatePushContent(Message message) {
        byte[] rawContent = message.getContent();
        String content = rawContent == null ? "" : new String(rawContent, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            if (isNotificationMessage(message)) {
                return MessageDisplayConstants.PUSH_CONTENT_NEW_SYSTEM_NOTIFICATION;
            }
            // 根据消息类型生成默认内容
            ContentType contentType = message.getContentType();
            if (contentType != null) {
                switch (contentType) {
                    case IMAGE: return MessageDisplayConstants.PUSH_CONTENT_IMAGE;
                    case VOICE: return MessageDisplayConstants.PUSH_CONTENT_VOICE;
                    case VIDEO: return MessageDisplayConstants.PUSH_CONTENT_VIDEO;
                    case FILE: return MessageDisplayConstants.PUSH_CONTENT_FILE;
                    case LOCATION: return MessageDisplayConstants.PUSH_CONTENT_LOCATION;
                    default: return MessageDisplayConstants.PUSH_TITLE_NEW_MESSAGE;
                }
            }
            return MessageDisplayConstants.PUSH_TITLE_NEW_MESSAGE;
        }
        
        // 限制内容长度
        if (content.length() > 100) {
            return content.substring(0, 97) + "...";
        }
        
        return content;
    }

    private boolean isNotificationMessage(Message message) {
        if (message == null) {
            return false;
        }
        if (message.getChatType() == ChatType.NOTIFICATION) {
            return true;
        }
        return message.getOptions() != null && message.getOptions().getNotification();
    }

    private List<PushMessage> createPushMessagesForUser(String userID, String title, String content, Message originalMessage) {
        List<PushMessage> pushMessages = new ArrayList<>();
        Map<Integer, String> tokens = deviceTokenService.getUserDeviceTokens(userID);
        tokens.forEach((platformId, token) -> {
            PushMessage pushMessage = createPushMessageForPlatform(userID, platformId, title, content, originalMessage);
            if (pushMessage != null) {
                pushMessage.setDeviceToken(token);
                pushMessages.add(pushMessage);
            }
        });
        return pushMessages;
    }

    private PushMessage createPushMessageForPlatform(String userID, Integer platformID, String title, String content, Message originalMessage) {
        try {
            PushMessage pushMessage = new PushMessage(userID, title, content);
            pushMessage.setPlatformID(platformID);

            if (originalMessage != null) {
                ChatType chatType = originalMessage.getChatType() == null
                        ? ChatType.PRIVATE : originalMessage.getChatType();
                ContentType contentType = originalMessage.getContentType();
                pushMessage.setMessageID(originalMessage.getServerMsgId());
                pushMessage.setSenderID(originalMessage.getSenderId());
                pushMessage.setSenderNickname(originalMessage.getSenderNickName());
                pushMessage.setMessageType(contentType == null ? ContentType.TEXT.getCode() : contentType.getCode());
                pushMessage.setConversationType(chatType.getCode());

                if (chatType == ChatType.PRIVATE) {
                    pushMessage.setConversationID("single_" + originalMessage.getSenderId() + "_" + originalMessage.getReceiverId());
                } else if (chatType == ChatType.GROUP) {
                    pushMessage.setConversationID("group_" + originalMessage.getGroupId());
                }

                Map<String, Object> extraData = new HashMap<>();
                if (originalMessage.getUniqueId() != null) {
                    extraData.put("uniqueID", originalMessage.getUniqueId());
                }
                pushMessage.setExtras(extraData);
            }

            configurePlatformSpecificProperties(pushMessage, platformID);
            return pushMessage;
        } catch (Exception e) {
            logger.error("创建平台推送消息失败: userID={}, platformID={}", userID, platformID, e);
            return null;
        }
    }

    private ChatType resolveSessionType(Integer sessionType) {
        if (sessionType == null) {
            return ChatType.PRIVATE;
        }
        try {
            return ChatType.fromCode(sessionType);
        } catch (IllegalArgumentException ex) {
            return ChatType.PRIVATE;
        }
    }

    private ContentType resolveContentType(Integer contentType) {
        if (contentType == null) {
            return null;
        }
        try {
            return ContentType.fromCode(contentType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void configurePlatformSpecificProperties(PushMessage pushMessage, Integer platformID) {
        switch (PlatformType.fromCode(platformID)) {
            case IOS:
                pushMessage.setSound("default");
                pushMessage.setBadge(1);
                pushMessage.setCategory("MESSAGE");
                break;
            case ANDROID:
                pushMessage.setSound("default");
                pushMessage.setPriority(2);
                break;
            default:
                pushMessage.setPriority(2);
                break;
        }
    }
    
    /**
     * Map转OfflinePushConfig
     */
    private OfflinePushConfig mapToOfflinePushConfig(Map<Object, Object> map) {
        OfflinePushConfig config = new OfflinePushConfig();
        config.setUserID((String) map.get("userID"));
        
        Object enabled = map.get("enabled");
        if (enabled != null) {
            config.setEnabled(Boolean.parseBoolean(enabled.toString()));
        }
        
        Object maxDailyCount = map.get("maxDailyCount");
        if (maxDailyCount != null) {
            config.setMaxDailyCount(Integer.parseInt(maxDailyCount.toString()));
        }
        
        Object currentDailyCount = map.get("currentDailyCount");
        if (currentDailyCount != null) {
            config.setCurrentDailyCount(Integer.parseInt(currentDailyCount.toString()));
        }
        
        config.setQuietStartTime((String) map.get("quietStartTime"));
        config.setQuietEndTime((String) map.get("quietEndTime"));
        
        Object allowDuringQuietTime = map.get("allowDuringQuietTime");
        if (allowDuringQuietTime != null) {
            config.setAllowDuringQuietTime(Boolean.parseBoolean(allowDuringQuietTime.toString()));
        }
        
        Object lastUpdateTime = map.get("lastUpdateTime");
        if (lastUpdateTime != null) {
            config.setLastUpdateTime(Long.parseLong(lastUpdateTime.toString()));
        }
        
        return config;
    }
    
    /**
     * OfflinePushConfig转Map
     */
    private Map<String, String> offlinePushConfigToMap(OfflinePushConfig config) {
        Map<String, String> map = new HashMap<>();
        map.put("userID", config.getUserID());
        map.put("enabled", String.valueOf(config.isEnabled()));
        map.put("maxDailyCount", String.valueOf(config.getMaxDailyCount()));
        map.put("quietStartTime", config.getQuietStartTime());
        map.put("quietEndTime", config.getQuietEndTime());
        map.put("allowDuringQuietTime", String.valueOf(config.isAllowDuringQuietTime()));
        map.put("lastUpdateTime", String.valueOf(config.getLastUpdateTime()));
        return map;
    }
}
