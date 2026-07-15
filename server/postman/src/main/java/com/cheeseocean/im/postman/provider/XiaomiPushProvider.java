package com.cheeseocean.im.postman.provider;

import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.postman.entity.PushMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * 小米推送服务实现
 * 基于小米Push SDK实现小米设备推送
 * 
 * @author xxxcrel
 */
@Service
public class XiaomiPushProvider implements PushProvider {
    
    private static final Logger logger = CommonLoggers.POSTMAN;
    
    @Value("${cheeseim.push.xiaomi.enabled:false}")
    private boolean enabled;
    
    @Value("${cheeseim.push.xiaomi.app-secret:}")
    private String appSecret;
    
    @Value("${cheeseim.push.xiaomi.package-name:}")
    private String packageName;
    
    @Value("${cheeseim.push.xiaomi.push-url:https://api.xmpush.xiaomi.com/v3/message/regid}")
    private String pushUrl;
    
    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    public XiaomiPushProvider(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("小米推送服务已禁用");
            return;
        }
        
        try {
            if (appSecret == null || appSecret.trim().isEmpty() ||
                packageName == null || packageName.trim().isEmpty()) {
                logger.warn("小米推送配置不完整，跳过初始化");
                return;
            }
            
            logger.info("小米推送服务初始化成功: packageName={}", packageName);
            
        } catch (Exception e) {
            logger.error("小米推送服务初始化失败", e);
            enabled = false;
        }
    }
    
    @Override
    public PushResult sendPush(PushMessage pushMessage) {
        if (!isAvailable()) {
            return PushResult.failure("小米推送服务不可用", getProviderName());
        }
        
        try {
            // 验证设备Token
            String deviceToken = pushMessage.getDeviceToken();
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                return PushResult.failure("设备Token不能为空", getProviderName());
            }
            
            // 构建推送消息
            Map<String, Object> message = buildPushMessage(pushMessage);
            
            // 发送推送
            long startTime = System.currentTimeMillis();
            String response = sendPushRequest(message);
            long responseTime = System.currentTimeMillis() - startTime;
            
            // 解析响应
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            String result = responseMap.get("result").toString();
            
            if ("ok".equals(result)) {
                // 推送成功
                String messageId = responseMap.get("id") != null ? 
                        responseMap.get("id").toString() : null;
                
                PushResult pushResult = PushResult.success(messageId, getProviderName());
                pushResult.setResponseTime(responseTime);
                pushResult.setMessageID(pushMessage.getMessageID());
                
                logger.info("小米推送成功: userID={}, deviceToken={}, messageId={}, responseTime={}ms", 
                           pushMessage.getUserID(), 
                           maskToken(deviceToken),
                           messageId, 
                           responseTime);
                
                return pushResult;
            } else {
                // 推送失败
                String description = responseMap.get("description") != null ? 
                        responseMap.get("description").toString() : "未知错误";
                
                PushResult pushResult = PushResult.failure(
                        "小米推送失败: " + result + " - " + description, getProviderName());
                pushResult.setResponseTime(responseTime);
                pushResult.setMessageID(pushMessage.getMessageID());
                
                logger.warn("小米推送失败: userID={}, deviceToken={}, result={}, description={}", 
                           pushMessage.getUserID(), 
                           maskToken(deviceToken),
                           result, 
                           description);
                
                return pushResult;
            }
            
        } catch (Exception e) {
            logger.error("小米推送异常: userID={}", pushMessage.getUserID(), e);
            return PushResult.failure("小米推送异常: " + e.getMessage(), getProviderName());
        }
    }
    
    /**
     * 构建推送消息
     */
    private Map<String, Object> buildPushMessage(PushMessage pushMessage) {
        Map<String, Object> message = new HashMap<>();
        
        // 设置目标设备
        message.put("registration_id", pushMessage.getDeviceToken());
        
        // 设置消息内容
        if (pushMessage.getTitle() != null && !pushMessage.getTitle().trim().isEmpty()) {
            message.put("title", pushMessage.getTitle());
        }
        
        if (pushMessage.getContent() != null && !pushMessage.getContent().trim().isEmpty()) {
            message.put("description", pushMessage.getContent());
        }
        
        // 设置通知类型
        message.put("notify_type", 1); // 通知栏消息
        
        // 设置包名
        message.put("restricted_package_name", packageName);
        
        // 设置优先级
        if (pushMessage.getPriority() != null) {
            switch (pushMessage.getPriority()) {
                case 0:
                    message.put("notify_id", 0); // 低优先级
                    break;
                case 2:
                    message.put("notify_id", 2); // 高优先级
                    break;
                default:
                    message.put("notify_id", 1); // 正常优先级
                    break;
            }
        }
        
        // 设置过期时间
        if (pushMessage.getExpireTime() != null) {
            long ttl = (pushMessage.getExpireTime() - System.currentTimeMillis()) / 1000;
            if (ttl > 0) {
                message.put("time_to_live", ttl);
            }
        } else {
            message.put("time_to_live", 86400); // 默认24小时
        }
        
        // 设置自定义数据
        Map<String, String> extra = new HashMap<>();
        if (pushMessage.getMessageID() != null) {
            extra.put("messageID", pushMessage.getMessageID());
        }
        if (pushMessage.getConversationID() != null) {
            extra.put("conversationID", pushMessage.getConversationID());
        }
        if (pushMessage.getSenderID() != null) {
            extra.put("senderID", pushMessage.getSenderID());
        }

        // 添加自定义扩展数据
        if (pushMessage.getExtras() != null && !pushMessage.getExtras().isEmpty()) {
            for (Map.Entry<String, Object> entry : pushMessage.getExtras().entrySet()) {
                extra.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        
        if (!extra.isEmpty()) {
            message.put("extra", extra);
        }
        
        return message;
    }
    
    /**
     * 发送推送请求
     */
    private String sendPushRequest(Map<String, Object> message) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "key=" + appSecret);
        
        // 构建表单数据
        StringBuilder formData = new StringBuilder();
        for (Map.Entry<String, Object> entry : message.entrySet()) {
            if (formData.length() > 0) {
                formData.append("&");
            }
            formData.append(entry.getKey()).append("=").append(entry.getValue());
        }
        
        HttpEntity<String> request = new HttpEntity<>(formData.toString(), headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(pushUrl, request, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        } else {
            throw new RuntimeException("小米推送请求失败: " + response.getStatusCode());
        }
    }
    
    @Override
    public String getProviderName() {
        return "Xiaomi";
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
        return enabled && appSecret != null && packageName != null;
    }
    
    @Override
    public ProviderConfig getConfig() {
        ProviderConfig config = new ProviderConfig(getProviderName(), enabled);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("packageName", packageName);
        properties.put("pushUrl", pushUrl);
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
