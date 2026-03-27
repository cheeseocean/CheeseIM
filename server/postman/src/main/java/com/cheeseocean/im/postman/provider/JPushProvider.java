package com.cheeseocean.im.postman.provider;

import cn.jiguang.common.ClientConfig;
import cn.jiguang.common.resp.APIConnectionException;
import cn.jiguang.common.resp.APIRequestException;
import cn.jpush.api.JPushClient;
import cn.jpush.api.push.model.Message;
import cn.jpush.api.push.model.Options;
import cn.jpush.api.push.model.Platform;
import cn.jpush.api.push.model.PushPayload;
import cn.jpush.api.push.model.audience.Audience;
import cn.jpush.api.push.model.notification.AndroidNotification;
import cn.jpush.api.push.model.notification.IosNotification;
import cn.jpush.api.push.model.notification.Notification;
import com.cheeseocean.im.common.core.enums.PlatformType;
import com.cheeseocean.im.postman.entity.PushMessage;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 极光推送服务实现
 * 基于JPush SDK实现多平台推送
 * 
 * @author xxxcrel
 */
@Service
public class JPushProvider implements PushProvider {
    
    private static final Logger logger = CommonLoggers.POSTMAN;
    
    @Value("${cheeseim.push.jpush.enabled:false}")
    private boolean enabled;
    
    @Value("${cheeseim.push.jpush.app-key:}")
    private String appKey;
    
    @Value("${cheeseim.push.jpush.master-secret:}")
    private String masterSecret;
    
    @Value("${cheeseim.push.jpush.production:true}")
    private boolean production;
    
    @Value("${cheeseim.push.jpush.time-to-live:86400}")
    private int timeToLive;
    
    private JPushClient jpushClient;
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("极光推送服务已禁用");
            return;
        }
        
        try {
            if (appKey == null || appKey.trim().isEmpty() ||
                masterSecret == null || masterSecret.trim().isEmpty()) {
                logger.warn("极光推送配置不完整，跳过初始化");
                return;
            }
            
            // 创建极光推送客户端
            ClientConfig clientConfig = ClientConfig.getInstance();
            clientConfig.setTimeToLive(timeToLive);
            clientConfig.setApnsProduction(production);
            
            jpushClient = new JPushClient(masterSecret, appKey, null, clientConfig);
            
            logger.info("极光推送服务初始化成功: appKey={}, production={}, timeToLive={}", 
                       appKey, production, timeToLive);
            
        } catch (Exception e) {
            logger.error("极光推送服务初始化失败", e);
            enabled = false;
        }
    }
    
    @Override
    public PushResult sendPush(PushMessage pushMessage) {
        if (!isAvailable()) {
            return PushResult.failure("极光推送服务不可用", getProviderName());
        }
        
        try {
            // 验证设备Token
            String deviceToken = pushMessage.getDeviceToken();
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                return PushResult.failure("设备Token不能为空", getProviderName());
            }
            
            // 构建推送负载
            PushPayload payload = buildPushPayload(pushMessage);
            
            // 发送推送
            long startTime = System.currentTimeMillis();
            cn.jpush.api.push.PushResult result = jpushClient.sendPush(payload);
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (result.isResultOK()) {
                PushResult pushResult = PushResult.success(
                        String.valueOf(result.msg_id), getProviderName());
                pushResult.setResponseTime(responseTime);
                pushResult.setMessageID(pushMessage.getMessageID());
                
                logger.info("极光推送成功: userID={}, deviceToken={}, msgId={}, responseTime={}ms", 
                           pushMessage.getUserID(), 
                           maskToken(deviceToken),
                           result.msg_id, 
                           responseTime);
                
                return pushResult;
            } else {
                PushResult pushResult = PushResult.failure(
                        "极光推送失败: " + result.error.getMessage(), getProviderName());
                pushResult.setResponseTime(responseTime);
                pushResult.setMessageID(pushMessage.getMessageID());
                
                logger.warn("极光推送失败: userID={}, deviceToken={}, error={}, responseTime={}ms", 
                           pushMessage.getUserID(), 
                           maskToken(deviceToken),
                           result.error.getMessage(), 
                           responseTime);
                
                return pushResult;
            }
            
        } catch (APIConnectionException e) {
            logger.error("极光推送连接异常: userID={}", pushMessage.getUserID(), e);
            return PushResult.failure("极光推送连接异常: " + e.getMessage(), getProviderName());
            
        } catch (APIRequestException e) {
            logger.error("极光推送请求异常: userID={}, errorCode={}", 
                        pushMessage.getUserID(), e.getErrorCode(), e);
            return PushResult.failure(
                    "极光推送请求异常: " + e.getErrorCode() + " - " + e.getErrorMessage(), 
                    getProviderName());
            
        } catch (Exception e) {
            logger.error("极光推送异常: userID={}", pushMessage.getUserID(), e);
            return PushResult.failure("极光推送异常: " + e.getMessage(), getProviderName());
        }
    }
    
    /**
     * 构建推送负载
     */
    private PushPayload buildPushPayload(PushMessage pushMessage) {
        PushPayload.Builder builder = PushPayload.newBuilder();
        
        // 设置平台
        PlatformType platformType = pushMessage.getPlatformType();
        Platform platform = determinePlatform(platformType);
        builder.setPlatform(platform);
        
        // 设置目标设备
        builder.setAudience(Audience.registrationId(pushMessage.getDeviceToken()));
        
        // 构建通知
        Notification.Builder notificationBuilder = Notification.newBuilder();
        
        if (pushMessage.getTitle() != null && !pushMessage.getTitle().trim().isEmpty()) {
            notificationBuilder.setAlert(pushMessage.getTitle());
        } else if (pushMessage.getContent() != null && !pushMessage.getContent().trim().isEmpty()) {
            notificationBuilder.setAlert(pushMessage.getContent());
        }
        
        // 根据平台设置特定通知
        if (platformType == PlatformType.IOS) {
            // iOS通知
            IosNotification.Builder iosBuilder = IosNotification.newBuilder();
            
            if (pushMessage.getTitle() != null && !pushMessage.getTitle().trim().isEmpty()) {
                iosBuilder.setAlert(pushMessage.getTitle());
            }
            
            if (pushMessage.getBadge() != null && pushMessage.getBadge() > 0) {
                iosBuilder.setBadge(pushMessage.getBadge());
            }
            
            if (pushMessage.getSound() != null && !pushMessage.getSound().trim().isEmpty()) {
                iosBuilder.setSound(pushMessage.getSound());
            } else {
                iosBuilder.setSound("default");
            }
            
            // 添加自定义数据
            if (pushMessage.getExtras() != null && !pushMessage.getExtras().isEmpty()) {
                for (Map.Entry<String, Object> entry : pushMessage.getExtras().entrySet()) {
                    iosBuilder.addExtra(entry.getKey(), entry.getValue().toString());
                }
            }
            
            // 添加消息相关数据
            if (pushMessage.getMessageID() != null) {
                iosBuilder.addExtra("messageID", pushMessage.getMessageID());
            }
            if (pushMessage.getConversationID() != null) {
                iosBuilder.addExtra("conversationID", pushMessage.getConversationID());
            }
            
            notificationBuilder.addPlatformNotification(iosBuilder.build());
            
        } else if (platformType == PlatformType.ANDROID) {
            // Android通知
            AndroidNotification.Builder androidBuilder = AndroidNotification.newBuilder();
            
            if (pushMessage.getTitle() != null && !pushMessage.getTitle().trim().isEmpty()) {
                androidBuilder.setTitle(pushMessage.getTitle());
            }
            
            if (pushMessage.getContent() != null && !pushMessage.getContent().trim().isEmpty()) {
                androidBuilder.setAlert(pushMessage.getContent());
            }
            
            // 设置优先级
            if (pushMessage.getPriority() != null) {
                switch (pushMessage.getPriority()) {
                    case 0:
                        androidBuilder.setPriority(0); // 低优先级
                        break;
                    case 2:
                        androidBuilder.setPriority(2); // 高优先级
                        break;
                    default:
                        androidBuilder.setPriority(1); // 正常优先级
                        break;
                }
            }
            
            // 添加自定义数据
            if (pushMessage.getExtras() != null && !pushMessage.getExtras().isEmpty()) {
                for (Map.Entry<String, Object> entry : pushMessage.getExtras().entrySet()) {
                    androidBuilder.addExtra(entry.getKey(), entry.getValue().toString());
                }
            }
            
            // 添加消息相关数据
            if (pushMessage.getMessageID() != null) {
                androidBuilder.addExtra("messageID", pushMessage.getMessageID());
            }
            if (pushMessage.getConversationID() != null) {
                androidBuilder.addExtra("conversationID", pushMessage.getConversationID());
            }
            
            notificationBuilder.addPlatformNotification(androidBuilder.build());
        }
        
        builder.setNotification(notificationBuilder.build());
        
        // 设置消息（透传消息）
        if (pushMessage.getExtras() != null && !pushMessage.getExtras().isEmpty()) {
            Message.Builder messageBuilder = Message.newBuilder();
            messageBuilder.setMsgContent(pushMessage.getContent() != null ? pushMessage.getContent() : "");
            
            for (Map.Entry<String, Object> entry : pushMessage.getExtras().entrySet()) {
                messageBuilder.addExtra(entry.getKey(), entry.getValue().toString());
            }
            
            builder.setMessage(messageBuilder.build());
        }
        
        // 设置选项
        Options.Builder optionsBuilder = Options.newBuilder();
        optionsBuilder.setApnsProduction(production);
        optionsBuilder.setTimeToLive(timeToLive);
        
        builder.setOptions(optionsBuilder.build());
        
        return builder.build();
    }
    
    /**
     * 确定推送平台
     */
    private Platform determinePlatform(PlatformType platformType) {
        if (platformType == null || platformType == PlatformType.UNKNOWN) {
            return Platform.all();
        }
        return switch (platformType) {
            case IOS -> Platform.ios();
            case ANDROID -> Platform.android();
            default -> Platform.all();
        };
    }
    
    @Override
    public String getProviderName() {
        return "JPush";
    }
    
    @Override
    public List<PlatformType> getSupportedPlatforms() {
        return Arrays.asList(PlatformType.IOS, PlatformType.ANDROID);
    }
    
    @Override
    public boolean supportsPlatform(PlatformType platformType) {
        return platformType == PlatformType.IOS || platformType == PlatformType.ANDROID;
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && jpushClient != null;
    }
    
    @Override
    public ProviderConfig getConfig() {
        ProviderConfig config = new ProviderConfig(getProviderName(), enabled);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("appKey", appKey);
        properties.put("production", production);
        properties.put("timeToLive", timeToLive);
        config.setProperties(properties);
        
        config.setSupportedPlatforms(getSupportedPlatforms());
        
        return config;
    }
    
    /**
     * 掩码Token用于日志输出
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 6) + "***" + token.substring(token.length() - 4);
    }
}
