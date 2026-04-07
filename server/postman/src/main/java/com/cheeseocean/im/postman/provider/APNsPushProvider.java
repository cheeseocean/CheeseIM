package com.cheeseocean.im.postman.provider;

import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.postman.entity.PushMessage;
import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.DeliveryPriority;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.PushType;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * APNs推送服务实现
 * 基于Apple Push Notification service实现iOS推送
 * 
 * @author xxxcrel
 */
@Service
public class APNsPushProvider implements PushProvider {
    
    private static final Logger logger = CommonLoggers.POSTMAN;
    
    @Value("${cheeseim.push.apns.enabled:false}")
    private boolean enabled;
    
    @Value("${cheeseim.push.apns.key-path:}")
    private String keyPath;
    
    @Value("${cheeseim.push.apns.key-id:}")
    private String keyId;
    
    @Value("${cheeseim.push.apns.team-id:}")
    private String teamId;
    
    @Value("${cheeseim.push.apns.bundle-id:}")
    private String bundleId;
    
    @Value("${cheeseim.push.apns.production:true}")
    private boolean production;
    
    private ApnsClient apnsClient;
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("APNs推送服务已禁用");
            return;
        }
        
        try {
            if (keyPath == null || keyPath.trim().isEmpty() ||
                keyId == null || keyId.trim().isEmpty() ||
                teamId == null || teamId.trim().isEmpty() ||
                bundleId == null || bundleId.trim().isEmpty()) {
                logger.warn("APNs推送配置不完整，跳过初始化");
                return;
            }
            
            // 加载APNs签名密钥
            File keyFile = new File(keyPath);
            if (!keyFile.exists()) {
                logger.error("APNs密钥文件不存在: {}", keyPath);
                return;
            }
            
            ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(keyFile, teamId, keyId);
            
            // 创建APNs客户端
            ApnsClientBuilder clientBuilder = new ApnsClientBuilder()
                    .setApnsServer(production ? 
                            ApnsClientBuilder.PRODUCTION_APNS_HOST : 
                            ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                    .setSigningKey(signingKey);
            
            apnsClient = clientBuilder.build();
            
            logger.info("APNs推送服务初始化成功: bundleId={}, production={}, keyId={}", 
                       bundleId, production, keyId);
            
        } catch (Exception e) {
            logger.error("APNs推送服务初始化失败", e);
            enabled = false;
        }
    }
    
    @Override
    public PushResult sendPush(PushMessage pushMessage) {
        if (!isAvailable()) {
            return PushResult.failure("APNs服务不可用", getProviderName());
        }
        
        try {
            // 验证设备Token
            String deviceToken = pushMessage.getDeviceToken();
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                return PushResult.failure("设备Token不能为空", getProviderName());
            }
            
            // 构建APNs负载
            ApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            
            // 设置通知内容
            if (pushMessage.getTitle() != null && !pushMessage.getTitle().trim().isEmpty()) {
                payloadBuilder.setAlertTitle(pushMessage.getTitle());
            }
            
            if (pushMessage.getContent() != null && !pushMessage.getContent().trim().isEmpty()) {
                payloadBuilder.setAlertBody(pushMessage.getContent());
            }
            
            // 设置角标
            if (pushMessage.getBadge() != null && pushMessage.getBadge() > 0) {
                payloadBuilder.setBadgeNumber(pushMessage.getBadge());
            }
            
            // 设置声音
            if (pushMessage.getSound() != null && !pushMessage.getSound().trim().isEmpty()) {
                payloadBuilder.setSoundFileName(pushMessage.getSound());
            } else {
                payloadBuilder.setSoundFileName("default");
            }
            
            // 设置分类
            if (pushMessage.getCategory() != null && !pushMessage.getCategory().trim().isEmpty()) {
                payloadBuilder.setCategoryName(pushMessage.getCategory());
            }
            
            // 设置自定义数据
            if (pushMessage.getExtras() != null && !pushMessage.getExtras().isEmpty()) {
                for (Map.Entry<String, Object> entry : pushMessage.getExtras().entrySet()) {
                    payloadBuilder.addCustomProperty(entry.getKey(), entry.getValue());
                }
            }
            
            // 添加消息相关数据
            if (pushMessage.getMessageID() != null) {
                payloadBuilder.addCustomProperty("messageID", pushMessage.getMessageID());
            }
            if (pushMessage.getConversationID() != null) {
                payloadBuilder.addCustomProperty("conversationID", pushMessage.getConversationID());
            }
            if (pushMessage.getSenderID() != null) {
                payloadBuilder.addCustomProperty("senderID", pushMessage.getSenderID());
            }
            
            String payload = payloadBuilder.build();
            
            // 创建推送通知
            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(
                    deviceToken, bundleId, payload);
            
            // 设置过期时间
            if (pushMessage.getExpireTime() != null) {
                pushNotification = new SimpleApnsPushNotification(
                        deviceToken, bundleId, payload, 
                        Instant.ofEpochMilli(pushMessage.getExpireTime()),
                        (DeliveryPriority) null, (PushType) null);
            }
            
            // 发送推送
            long startTime = System.currentTimeMillis();
            PushNotificationResponse<SimpleApnsPushNotification> response = 
                    apnsClient.sendNotification(pushNotification).get();
            long responseTime = System.currentTimeMillis() - startTime;
            
            if (response.isAccepted()) {
                PushResult result = PushResult.success(
                        response.getApnsId() != null ? response.getApnsId().toString() : null, 
                        getProviderName());
                result.setResponseTime(responseTime);
                result.setMessageID(pushMessage.getMessageID());
                
                logger.info("APNs推送成功: userID={}, deviceToken={}, apnsId={}, responseTime={}ms", 
                           pushMessage.getUserID(), 
                           maskToken(deviceToken),
                           response.getApnsId(), 
                           responseTime);
                
                return result;
            } else {
                String rejectionReason = response.getRejectionReason().get();
                
                PushResult result = PushResult.failure(
                        "APNs推送被拒绝: " + rejectionReason, getProviderName());
                result.setResponseTime(responseTime);
                result.setMessageID(pushMessage.getMessageID());
                
                logger.warn("APNs推送被拒绝: userID={}, deviceToken={}, reason={}, responseTime={}ms", 
                           pushMessage.getUserID(), 
                           maskToken(deviceToken),
                           rejectionReason, 
                           responseTime);
                
                return result;
            }
            
        } catch (ExecutionException e) {
            logger.error("APNs推送执行异常: userID={}", pushMessage.getUserID(), e);
            return PushResult.failure("APNs推送执行异常: " + e.getMessage(), getProviderName());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("APNs推送被中断: userID={}", pushMessage.getUserID(), e);
            return PushResult.failure("APNs推送被中断: " + e.getMessage(), getProviderName());
            
        } catch (Exception e) {
            logger.error("APNs推送异常: userID={}", pushMessage.getUserID(), e);
            return PushResult.failure("APNs推送异常: " + e.getMessage(), getProviderName());
        }
    }
    
    @Override
    public String getProviderName() {
        return "APNs";
    }
    
    @Override
    public List<PlatformType> getSupportedPlatforms() {
        return Arrays.asList(PlatformType.IOS);
    }
    
    @Override
    public boolean supportsPlatform(PlatformType platformType) {
        return platformType == PlatformType.IOS;
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && apnsClient != null;
    }
    
    @Override
    public ProviderConfig getConfig() {
        ProviderConfig config = new ProviderConfig(getProviderName(), enabled);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("keyPath", keyPath);
        properties.put("keyId", keyId);
        properties.put("teamId", teamId);
        properties.put("bundleId", bundleId);
        properties.put("production", production);
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
    
    /**
     * 销毁服务
     */
    @PreDestroy
    public void destroy() {
        if (apnsClient != null) {
            try {
                apnsClient.close();
                logger.info("APNs客户端已关闭");
            } catch (Exception e) {
                logger.error("关闭APNs客户端失败", e);
            }
        }
    }
}
