package com.cheeseocean.im.push.provider;

import com.cheeseocean.im.common.core.enums.PlatformType;
import com.cheeseocean.im.push.entity.PushMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * 华为推送服务实现
 * 基于华为Push Kit实现华为设备推送
 * 
 * @author CheeseIM
 */
@Service
public class HuaweiPushProvider implements PushProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(HuaweiPushProvider.class);
    
    @Value("${cheeseim.push.huawei.enabled:false}")
    private boolean enabled;
    
    @Value("${cheeseim.push.huawei.app-id:}")
    private String appId;
    
    @Value("${cheeseim.push.huawei.app-secret:}")
    private String appSecret;
    
    @Value("${cheeseim.push.huawei.auth-url:https://oauth-login.cloud.huawei.com/oauth2/v3/token}")
    private String authUrl;
    
    @Value("${cheeseim.push.huawei.push-url:https://push-api.cloud.huawei.com/v1}")
    private String pushUrl;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String accessToken;
    private long tokenExpireTime;
    
    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("华为推送服务已禁用");
            return;
        }
        
        try {
            if (appId == null || appId.trim().isEmpty() || 
                appSecret == null || appSecret.trim().isEmpty()) {
                logger.warn("华为推送配置不完整，跳过初始化");
                return;
            }
            
            // 获取访问令牌
            refreshAccessToken();
            
            logger.info("华为推送服务初始化成功: appId={}", appId);
            
        } catch (Exception e) {
            logger.error("华为推送服务初始化失败", e);
            enabled = false;
        }
    }
    
    @Override
    public PushResult sendPush(PushMessage pushMessage) {
        if (!isAvailable()) {
            return PushResult.failure("华为推送服务不可用", getProviderName());
        }
        
        try {
            // 验证设备Token
            String deviceToken = pushMessage.getDeviceToken();
            if (deviceToken == null || deviceToken.trim().isEmpty()) {
                return PushResult.failure("设备Token不能为空", getProviderName());
            }
            
            // 检查并刷新访问令牌
            if (needRefreshToken()) {
                refreshAccessToken();
            }
            
            // 构建推送消息
            Map<String, Object> message = buildPushMessage(pushMessage);
            
            // 发送推送
            long startTime = System.currentTimeMillis();
            String response = sendPushRequest(message);
            long responseTime = System.currentTimeMillis() - startTime;
            
            // 解析响应
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            String code = responseMap.get("code").toString();
            
            if ("80000000".equals(code)) {
                // 推送成功
                String requestId = responseMap.get("requestId") != null ? 
                        responseMap.get("requestId").toString() : null;
                
                PushResult result = PushResult.success(requestId, getProviderName());
                result.setResponseTime(responseTime);
                result.setMessageID(pushMessage.getMessageID());
                
                logger.info("华为推送成功: userID={}, deviceToken={}, requestId={}, responseTime={}ms", 
                           pushMessage.getUserID(), 
                           maskToken(deviceToken),
                           requestId, 
                           responseTime);
                
                return result;
            } else {
                // 推送失败
                String msg = responseMap.get("msg") != null ? responseMap.get("msg").toString() : "未知错误";
                
                PushResult result = PushResult.failure(
                        "华为推送失败: " + code + " - " + msg, getProviderName());
                result.setResponseTime(responseTime);
                result.setMessageID(pushMessage.getMessageID());
                
                logger.warn("华为推送失败: userID={}, deviceToken={}, code={}, msg={}", 
                           pushMessage.getUserID(), 
                           maskToken(deviceToken),
                           code, 
                           msg);
                
                return result;
            }
            
        } catch (Exception e) {
            logger.error("华为推送异常: userID={}", pushMessage.getUserID(), e);
            return PushResult.failure("华为推送异常: " + e.getMessage(), getProviderName());
        }
    }
    
    /**
     * 构建推送消息
     */
    private Map<String, Object> buildPushMessage(PushMessage pushMessage) {
        Map<String, Object> message = new HashMap<>();
        
        // 设置通知消息
        Map<String, Object> notification = new HashMap<>();
        if (pushMessage.getTitle() != null && !pushMessage.getTitle().trim().isEmpty()) {
            notification.put("title", pushMessage.getTitle());
        }
        if (pushMessage.getContent() != null && !pushMessage.getContent().trim().isEmpty()) {
            notification.put("body", pushMessage.getContent());
        }
        
        // Android特定配置
        Map<String, Object> androidNotification = new HashMap<>();
        if (pushMessage.getSound() != null && !pushMessage.getSound().trim().isEmpty()) {
            androidNotification.put("sound", pushMessage.getSound());
        }
        androidNotification.put("default_sound", true);
        androidNotification.put("channel_id", "default");
        
        // 设置点击行为
        Map<String, Object> clickAction = new HashMap<>();
        clickAction.put("type", 1); // 打开应用
        androidNotification.put("click_action", clickAction);
        
        notification.put("android", androidNotification);
        message.put("notification", notification);
        
        // 设置数据消息
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
        if (pushMessage.getPushType() != null) {
            data.put("pushType", pushMessage.getPushType().toString());
        }
        
        // 添加自定义数据
        if (pushMessage.getExtras() != null && !pushMessage.getExtras().isEmpty()) {
            for (Map.Entry<String, Object> entry : pushMessage.getExtras().entrySet()) {
                data.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        
        if (!data.isEmpty()) {
            try {
                message.put("data", objectMapper.writeValueAsString(data));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize Huawei push payload", e);
            }
        }
        
        // 设置Android特定配置
        Map<String, Object> android = new HashMap<>();
        android.put("collapse_key", -1);
        android.put("urgency", "HIGH");
        android.put("ttl", "86400s");
        message.put("android", android);
        
        // 设置目标设备
        List<String> tokens = Arrays.asList(pushMessage.getDeviceToken());
        message.put("token", tokens);
        
        return message;
    }
    
    /**
     * 发送推送请求
     */
    private String sendPushRequest(Map<String, Object> message) throws Exception {
        String url = pushUrl + "/" + appId + "/messages:send";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("validate_only", false);
        requestBody.put("message", message);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        } else {
            throw new RuntimeException("华为推送请求失败: " + response.getStatusCode());
        }
    }
    
    /**
     * 刷新访问令牌
     */
    private void refreshAccessToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        String body = "grant_type=client_credentials" +
                "&client_id=" + appId +
                "&client_secret=" + appSecret;
        
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(authUrl, request, String.class);
        
        if (response.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
            
            this.accessToken = responseMap.get("access_token").toString();
            Integer expiresIn = Integer.parseInt(responseMap.get("expires_in").toString());
            this.tokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L; // 提前5分钟刷新
            
            logger.info("华为推送访问令牌已刷新: expiresIn={}", expiresIn);
        } else {
            throw new RuntimeException("获取华为推送访问令牌失败: " + response.getStatusCode());
        }
    }
    
    /**
     * 检查是否需要刷新令牌
     */
    private boolean needRefreshToken() {
        return accessToken == null || System.currentTimeMillis() >= tokenExpireTime;
    }
    
    @Override
    public String getProviderName() {
        return "Huawei";
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
        return enabled && appId != null && appSecret != null;
    }
    
    @Override
    public ProviderConfig getConfig() {
        ProviderConfig config = new ProviderConfig(getProviderName(), enabled);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("appId", appId);
        properties.put("authUrl", authUrl);
        properties.put("pushUrl", pushUrl);
        properties.put("hasAccessToken", accessToken != null);
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
