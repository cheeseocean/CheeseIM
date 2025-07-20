package com.cheeseocean.im.push.controller;

import com.cheeseocean.im.push.service.PushConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 推送配置控制器
 * 提供推送配置相关的REST API
 * 
 * @author CheeseIM
 */
@RestController
@RequestMapping("/api/v1/push/config")
public class PushConfigController {
    
    private static final Logger logger = LoggerFactory.getLogger(PushConfigController.class);
    
    @Autowired
    private PushConfigService pushConfigService;
    
    /**
     * 获取用户的群聊读取类型
     */
    @GetMapping("/group-chat-read-type")
    public ResponseEntity<Map<String, Object>> getGroupChatReadType(
            @RequestParam String userID,
            @RequestParam(required = false) String groupID) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            PushConfigService.GroupChatReadType readType = pushConfigService.getUserGroupChatReadType(userID, groupID);
            
            response.put("success", true);
            response.put("userID", userID);
            response.put("groupID", groupID);
            response.put("readType", readType.name());
            response.put("readTypeValue", readType.getValue());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取群聊读取类型失败: userID={}, groupID={}", userID, groupID, e);
            
            response.put("success", false);
            response.put("error", "获取群聊读取类型失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 设置用户的群聊读取类型
     */
    @PostMapping("/group-chat-read-type")
    public ResponseEntity<Map<String, Object>> setGroupChatReadType(
            @RequestParam String userID,
            @RequestParam(required = false) String groupID,
            @RequestParam String readType) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            PushConfigService.GroupChatReadType type = PushConfigService.GroupChatReadType.valueOf(readType);
            boolean success = pushConfigService.setUserGroupChatReadType(userID, groupID, type);
            
            response.put("success", success);
            response.put("userID", userID);
            response.put("groupID", groupID);
            response.put("readType", readType);
            
            if (success) {
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "设置群聊读取类型失败");
                return ResponseEntity.internalServerError().body(response);
            }
            
        } catch (IllegalArgumentException e) {
            logger.error("无效的群聊读取类型: userID={}, groupID={}, readType={}", 
                        userID, groupID, readType, e);
            
            response.put("success", false);
            response.put("error", "无效的群聊读取类型: " + readType);
            
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            logger.error("设置群聊读取类型失败: userID={}, groupID={}, readType={}", 
                        userID, groupID, readType, e);
            
            response.put("success", false);
            response.put("error", "设置群聊读取类型失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取用户推送启用状态
     */
    @GetMapping("/push-enabled")
    public ResponseEntity<Map<String, Object>> getPushEnabled(@RequestParam String userID) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean pushEnabled = pushConfigService.isPushEnabled(userID);
            boolean offlinePushEnabled = pushConfigService.isOfflinePushEnabled(userID);
            
            response.put("success", true);
            response.put("userID", userID);
            response.put("pushEnabled", pushEnabled);
            response.put("offlinePushEnabled", offlinePushEnabled);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取用户推送启用状态失败: userID={}", userID, e);
            
            response.put("success", false);
            response.put("error", "获取推送启用状态失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取用户群组推送启用状态
     */
    @GetMapping("/group-push-enabled")
    public ResponseEntity<Map<String, Object>> getGroupPushEnabled(
            @RequestParam String userID,
            @RequestParam String groupID) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean enabled = pushConfigService.isGroupPushEnabled(userID, groupID);
            
            response.put("success", true);
            response.put("userID", userID);
            response.put("groupID", groupID);
            response.put("enabled", enabled);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取用户群组推送启用状态失败: userID={}, groupID={}", userID, groupID, e);
            
            response.put("success", false);
            response.put("error", "获取群组推送启用状态失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取系统默认配置
     */
    @GetMapping("/default")
    public ResponseEntity<Map<String, Object>> getDefaultConfig() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            PushConfigService.GroupChatReadType defaultReadType = pushConfigService.getDefaultGroupChatReadType();
            
            response.put("success", true);
            response.put("defaultGroupChatReadType", defaultReadType.name());
            response.put("defaultGroupChatReadTypeValue", defaultReadType.getValue());
            
            // 添加所有可用的群聊读取类型
            Map<String, Object> availableTypes = new HashMap<>();
            for (PushConfigService.GroupChatReadType type : PushConfigService.GroupChatReadType.values()) {
                availableTypes.put(type.name(), type.getValue());
            }
            response.put("availableGroupChatReadTypes", availableTypes);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取系统默认配置失败", e);
            
            response.put("success", false);
            response.put("error", "获取系统默认配置失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取用户完整的推送配置
     */
    @GetMapping("/user/{userID}")
    public ResponseEntity<Map<String, Object>> getUserConfig(@PathVariable String userID) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取用户的各种推送配置
            boolean pushEnabled = pushConfigService.isPushEnabled(userID);
            boolean offlinePushEnabled = pushConfigService.isOfflinePushEnabled(userID);
            PushConfigService.GroupChatReadType globalGroupChatReadType = pushConfigService.getUserGlobalGroupChatReadType(userID);
            
            response.put("success", true);
            response.put("userID", userID);
            response.put("pushEnabled", pushEnabled);
            response.put("offlinePushEnabled", offlinePushEnabled);
            response.put("globalGroupChatReadType", globalGroupChatReadType.name());
            response.put("globalGroupChatReadTypeValue", globalGroupChatReadType.getValue());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取用户推送配置失败: userID={}", userID, e);
            
            response.put("success", false);
            response.put("error", "获取用户推送配置失败: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
