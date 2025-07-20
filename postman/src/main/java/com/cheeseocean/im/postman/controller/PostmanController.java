package com.cheeseocean.im.postman.controller;

import com.cheeseocean.im.postman.service.MessageStatisticsService;
import com.cheeseocean.im.postman.service.OnlineUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Postman消息传输控制器
 * 提供REST API用于监控和管理消息传输服务
 * 
 * @author CheeseIM
 */
@RestController
@RequestMapping("/api/v1/postman")
public class PostmanController {
    
    private static final Logger logger = LoggerFactory.getLogger(PostmanController.class);
    
    @Autowired
    private MessageStatisticsService messageStatisticsService;
    
    @Autowired
    private OnlineUserService onlineUserService;
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "CheeseIM Postman Message Transfer");
        result.put("timestamp", System.currentTimeMillis());
        result.put("version", "1.0.0");
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取服务状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 基本状态
        status.put("service", "postman");
        status.put("status", "running");
        status.put("startTime", System.currentTimeMillis()); // 简化处理
        
        // 在线用户统计
        OnlineUserService.OnlineUserStats onlineStats = onlineUserService.getOnlineUserStats();
        status.put("onlineUsers", onlineStats.getTotalOnlineUsers());
        status.put("totalConnections", onlineStats.getTotalConnections());
        status.put("platformStats", onlineStats.getPlatformStats());
        
        // 消息传输统计
        MessageStatisticsService.MessageTransferStats transferStats = 
            messageStatisticsService.getMessageTransferStats();
        status.put("totalMessages", transferStats.getTotalMessages());
        status.put("successMessages", transferStats.getSuccessMessages());
        status.put("failedMessages", transferStats.getFailedMessages());
        status.put("successRate", transferStats.getSuccessRate());
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * 获取消息传输统计
     */
    @GetMapping("/stats/transfer")
    public ResponseEntity<MessageStatisticsService.MessageTransferStats> getTransferStats() {
        MessageStatisticsService.MessageTransferStats stats = 
            messageStatisticsService.getMessageTransferStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 获取实时统计
     */
    @GetMapping("/stats/realtime")
    public ResponseEntity<MessageStatisticsService.RealtimeStats> getRealtimeStats() {
        MessageStatisticsService.RealtimeStats stats = 
            messageStatisticsService.getRealtimeStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 获取在线用户统计
     */
    @GetMapping("/stats/online")
    public ResponseEntity<OnlineUserService.OnlineUserStats> getOnlineStats() {
        OnlineUserService.OnlineUserStats stats = onlineUserService.getOnlineUserStats();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 获取所有在线用户
     */
    @GetMapping("/users/online")
    public ResponseEntity<java.util.Set<String>> getOnlineUsers() {
        java.util.Set<String> onlineUsers = onlineUserService.getAllOnlineUsers();
        return ResponseEntity.ok(onlineUsers);
    }
    
    /**
     * 检查用户是否在线
     */
    @GetMapping("/users/{userID}/online")
    public ResponseEntity<Map<String, Object>> checkUserOnline(@PathVariable String userID) {
        boolean isOnline = onlineUserService.isUserOnline(userID);
        java.util.List<Integer> onlinePlatforms = onlineUserService.getUserOnlinePlatforms(userID);
        
        Map<String, Object> result = new HashMap<>();
        result.put("userID", userID);
        result.put("online", isOnline);
        result.put("onlinePlatforms", onlinePlatforms);
        result.put("platformCount", onlinePlatforms.size());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 设置用户在线状态（用于测试）
     */
    @PostMapping("/users/{userID}/online")
    public ResponseEntity<Map<String, Object>> setUserOnlineStatus(@PathVariable String userID,
                                                                  @RequestParam Integer platformID,
                                                                  @RequestParam boolean online) {
        try {
            if (onlineUserService instanceof com.cheeseocean.im.postman.service.impl.OnlineUserServiceImpl) {
                com.cheeseocean.im.postman.service.impl.OnlineUserServiceImpl impl = 
                    (com.cheeseocean.im.postman.service.impl.OnlineUserServiceImpl) onlineUserService;
                impl.setUserOnlineStatus(userID, platformID, online);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("userID", userID);
                result.put("platformID", platformID);
                result.put("online", online);
                result.put("message", online ? "用户已设置为在线" : "用户已设置为离线");
                
                logger.info("用户在线状态已更新: userID={}, platformID={}, online={}", 
                           userID, platformID, online);
                
                return ResponseEntity.ok(result);
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "不支持的操作");
                return ResponseEntity.badRequest().body(error);
            }
            
        } catch (Exception e) {
            logger.error("设置用户在线状态失败: userID={}, platformID={}, online={}", 
                        userID, platformID, online, e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "设置在线状态失败");
            error.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 重置统计信息
     */
    @PostMapping("/stats/reset")
    public ResponseEntity<Map<String, Object>> resetStats() {
        try {
            messageStatisticsService.resetStats();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "统计信息已重置");
            result.put("resetTime", System.currentTimeMillis());
            
            logger.info("统计信息已通过API重置");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("重置统计信息失败", e);
            
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "重置统计信息失败");
            error.put("message", e.getMessage());
            
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    /**
     * 获取服务配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        
        // 基本配置
        config.put("serviceName", "postman");
        config.put("serviceDescription", "CheeseIM Message Transfer Service");
        config.put("version", "1.0.0");
        
        // Kafka配置（简化显示）
        Map<String, Object> kafkaConfig = new HashMap<>();
        kafkaConfig.put("enabled", true);
        kafkaConfig.put("topics", java.util.Arrays.asList(
            "cheese_im_to_redis",
            "cheese_im_to_push", 
            "cheese_im_to_mongo",
            "cheese_im_msg_status_update"
        ));
        config.put("kafka", kafkaConfig);
        
        // Redis配置
        Map<String, Object> redisConfig = new HashMap<>();
        redisConfig.put("enabled", true);
        redisConfig.put("database", 0);
        config.put("redis", redisConfig);
        
        return ResponseEntity.ok(config);
    }
    
    /**
     * 获取系统信息
     */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> systemInfo = new HashMap<>();
        
        Runtime runtime = Runtime.getRuntime();
        
        // JVM信息
        Map<String, Object> jvmInfo = new HashMap<>();
        jvmInfo.put("totalMemory", runtime.totalMemory());
        jvmInfo.put("freeMemory", runtime.freeMemory());
        jvmInfo.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        jvmInfo.put("maxMemory", runtime.maxMemory());
        jvmInfo.put("availableProcessors", runtime.availableProcessors());
        systemInfo.put("jvm", jvmInfo);
        
        // 系统属性
        Map<String, Object> systemProps = new HashMap<>();
        systemProps.put("javaVersion", System.getProperty("java.version"));
        systemProps.put("osName", System.getProperty("os.name"));
        systemProps.put("osVersion", System.getProperty("os.version"));
        systemProps.put("osArch", System.getProperty("os.arch"));
        systemInfo.put("system", systemProps);
        
        // 时间信息
        systemInfo.put("currentTime", System.currentTimeMillis());
        systemInfo.put("timezone", java.util.TimeZone.getDefault().getID());
        
        return ResponseEntity.ok(systemInfo);
    }
}
