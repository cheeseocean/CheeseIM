package com.cheeseocean.im.postman.provider;

import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.postman.entity.PushMessage;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FCM推送服务实现
 * 基于Firebase Cloud Messaging实现Android推送
 * 
 * @author xxxcrel
 */
@Service
public class FCMPushProvider implements PushProvider {
    
    private static final Logger logger = CommonLoggers.POSTMAN;
    
    @Value("${cheeseim.push.fcm.enabled:false}")
    private boolean enabled;
    
    @Value("${cheeseim.push.fcm.service-account-key:}")
    private String serviceAccountKeyPath;
    
    @Value("${cheeseim.push.fcm.project-id:}")
    private String projectId;
    
    @Value("${cheeseim.push.fcm.app-name:cheese-im-fcm}")
    private String appName;
    
    private FirebaseMessaging firebaseMessaging;
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("FCM推送服务已禁用");
            return;
        }
        
        try {
            if (serviceAccountKeyPath == null || serviceAccountKeyPath.trim().isEmpty()) {
                logger.warn("FCM服务账号密钥路径未配置，跳过初始化");
                return;
            }
            
            // 检查是否已经初始化
            boolean appExists = false;
            for (FirebaseApp app : FirebaseApp.getApps()) {
                if (appName.equals(app.getName())) {
                    appExists = true;
                    break;
                }
            }
            
            if (!appExists) {
                // 初始化Firebase
                FileInputStream serviceAccount = new FileInputStream(serviceAccountKeyPath);
                
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setProjectId(projectId)
                        .build();
                
                FirebaseApp.initializeApp(options, appName);
            }
            
            firebaseMessaging = FirebaseMessaging.getInstance(FirebaseApp.getInstance(appName));
            
            logger.info("FCM推送服务初始化成功: projectId={}, appName={}", projectId, appName);
            
        } catch (IOException e) {
            logger.error("FCM推送服务初始化失败: 无法读取服务账号密钥文件", e);
            enabled = false;
        } catch (Exception e) {
            logger.error("FCM推送服务初始化失败", e);
            enabled = false;
        }
    }
    
    @Override
    public PushResult sendPush(PushMessage pushMessage) {
        if (!isAvailable()) {
            return PushResult.failure("FCM服务不可用", getProviderName());
        }
        
        try {
            // 验证设备Token
            String deviceToken = pushMessage.getDeviceToken();
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                return PushResult.failure("设备Token不能为空", getProviderName());
            }
            
            // 构建FCM消息
            Message.Builder messageBuilder = Message.builder();
            
            // 设置目标设备Token
            messageBuilder.setToken(deviceToken);
            
            // 构建通知
            Notification.Builder notificationBuilder = Notification.builder();
            
            if (pushMessage.getTitle() != null && !pushMessage.getTitle().trim().isEmpty()) {
                notificationBuilder.setTitle(pushMessage.getTitle());
            }
            
            if (pushMessage.getContent() != null && !pushMessage.getContent().trim().isEmpty()) {
                notificationBuilder.setBody(pushMessage.getContent());
            }
            
            messageBuilder.setNotification(notificationBuilder.build());
            
            // 构建数据负载
            Map<String, String> data = new HashMap<>();
            if (pushMessage.getMessageID() != null) {
                data.put("messageID", pushMessage.getMessageID());
            }
            if (pushMessage.getConversationID() != null) {
                data.put("conversationID", pushMessage.getConversationID());
            }
            if (pushMessage.getSenderID() != null) {
                data.put("senderID", pushMessage.getSenderID());
            }

            // 添加自定义数据
            if (pushMessage.getExtras() != null && !pushMessage.getExtras().isEmpty()) {
                for (Map.Entry<String, Object> entry : pushMessage.getExtras().entrySet()) {
                    data.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            
            if (!data.isEmpty()) {
                messageBuilder.putAllData(data);
            }
            
            // 构建Android特定配置
            AndroidConfig.Builder androidConfigBuilder = AndroidConfig.builder();
            
            // 设置优先级
            if (pushMessage.getPriority() != null) {
                switch (pushMessage.getPriority()) {
                    case 0: // 低优先级
                        androidConfigBuilder.setPriority(AndroidConfig.Priority.NORMAL);
                        break;
                    case 2: // 高优先级
                        androidConfigBuilder.setPriority(AndroidConfig.Priority.HIGH);
                        break;
                    default: // 正常优先级
                        androidConfigBuilder.setPriority(AndroidConfig.Priority.NORMAL);
                        break;
                }
            }
            
            // 设置通知配置
            AndroidNotification.Builder androidNotificationBuilder = AndroidNotification.builder();
            
            if (pushMessage.getSound() != null && !pushMessage.getSound().trim().isEmpty()) {
                androidNotificationBuilder.setSound(pushMessage.getSound());
            }
            
            // 设置通道ID
            Object channelId = pushMessage.getExtras() != null ? pushMessage.getExtras().get("android_channel_id") : null;
            if (channelId != null) {
                androidNotificationBuilder.setChannelId(channelId.toString());
            } else {
                androidNotificationBuilder.setChannelId("default");
            }
            
            androidConfigBuilder.setNotification(androidNotificationBuilder.build());
            
            // 设置TTL
            if (pushMessage.getExpireTime() != null) {
                long ttl = pushMessage.getExpireTime() - System.currentTimeMillis();
                if (ttl > 0) {
                    androidConfigBuilder.setTtl(ttl);
                }
            }
            
            messageBuilder.setAndroidConfig(androidConfigBuilder.build());
            
            Message message = messageBuilder.build();
            
            // 发送推送
            long startTime = System.currentTimeMillis();
            String response = firebaseMessaging.send(message);
            long responseTime = System.currentTimeMillis() - startTime;
            
            PushResult result = PushResult.success(response, getProviderName());
            result.setResponseTime(responseTime);
            result.setMessageID(pushMessage.getMessageID());
            
            logger.info("FCM推送成功: userID={}, deviceToken={}, response={}, responseTime={}ms", 
                       pushMessage.getUserID(), 
                       maskToken(deviceToken),
                       response, 
                       responseTime);
            
            return result;
            
        } catch (FirebaseMessagingException e) {
            String errorCode = e.getErrorCode().toString();
            String errorMessage = "FCM推送失败: " + errorCode + " - " + e.getMessage();
            
            PushResult result = PushResult.failure(errorMessage, getProviderName());
            result.setMessageID(pushMessage.getMessageID());
            
            logger.warn("FCM推送失败: userID={}, deviceToken={}, errorCode={}, message={}", 
                       pushMessage.getUserID(), 
                       maskToken(pushMessage.getDeviceToken()),
                       errorCode, 
                       e.getMessage());
            
            return result;
            
        } catch (Exception e) {
            logger.error("FCM推送异常: userID={}", pushMessage.getUserID(), e);
            return PushResult.failure("FCM推送异常: " + e.getMessage(), getProviderName());
        }
    }
    
    @Override
    public String getProviderName() {
        return "FCM";
    }
    
    @Override
    public List<PlatformType> getSupportedPlatforms() {
        return Arrays.asList(PlatformType.ANDROID);
    }
    
    @Override
    public boolean supportsPlatform(PlatformType platformType) {
        return platformType == PlatformType.ANDROID;
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && firebaseMessaging != null;
    }
    
    @Override
    public ProviderConfig getConfig() {
        ProviderConfig config = new ProviderConfig(getProviderName(), enabled);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("serviceAccountKeyPath", serviceAccountKeyPath);
        properties.put("projectId", projectId);
        properties.put("appName", appName);
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
