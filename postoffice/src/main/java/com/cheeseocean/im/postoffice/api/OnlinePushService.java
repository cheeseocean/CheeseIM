package com.cheeseocean.im.postoffice.api;

import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.postoffice.api.param.OnlinePushResult;
import com.cheeseocean.im.postoffice.api.param.UsersOnlineStatusResp;

import java.util.List;

/**
 * 在线推送服务接口
 *
 * @author CheeseIM
 */
public interface OnlinePushService {
    
    /**
     * 推送消息给多个用户
     * 
     * @param message 消息对象
     * @param userIDs 目标用户列表
     * @return 在线推送结果
     */
    OnlinePushResult pushMessageToUsers(Message message, List<String> userIDs);

    /**
     * 批量获取用户在线状态
     *
     * @param userIDs
     * @return 用户在线状态
     */
    UsersOnlineStatusResp getUserOnlineStatus(List<String> userIDs);

}
