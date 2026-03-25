package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.postman.entity.OfflinePushConfig;
import com.cheeseocean.im.postman.entity.OfflinePushResult;

import java.util.List;

/**
 * 离线推送服务接口
 * 通过第三方推送服务进行离线推送
 * 
 * @author CheeseIM
 */
public interface OfflinePushService {
    
    /**
     * 推送消息给多个用户
     * 
     * @param message 消息对象
     * @param targetUsers 目标用户列表
     * @return 离线推送结果
     */
    OfflinePushResult pushMessageToUsers(Message message, List<String> targetUsers);
    
    /**
     * 推送消息给单个用户
     * 
     * @param message 消息对象
     * @param userID 目标用户ID
     * @return 离线推送结果
     */
    OfflinePushResult pushMessageToUser(Message message, String userID);
    
    /**
     * 检查用户是否启用离线推送
     * 
     * @param userID 用户ID
     * @return 是否启用离线推送
     */
    boolean isOfflinePushEnabled(String userID);
    
    /**
     * 获取用户离线推送配置
     * 
     * @param userID 用户ID
     * @return 用户离线推送配置
     */
    OfflinePushConfig getUserOfflinePushConfig(String userID);
    
    /**
     * 更新用户离线推送配置
     * 
     * @param userID 用户ID
     * @param config 离线推送配置
     * @return 是否更新成功
     */
    boolean updateUserOfflinePushConfig(String userID, OfflinePushConfig config);
}
