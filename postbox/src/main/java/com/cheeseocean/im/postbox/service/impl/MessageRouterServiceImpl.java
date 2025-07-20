package com.cheeseocean.im.postbox.service.impl;

import com.cheeseocean.im.common.constants.MessageConstants;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.postbox.service.MessageRouterService;
import com.cheeseocean.im.postbox.service.OnlineUserService;
import com.cheeseocean.im.postbox.service.GroupMemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息路由服务实现
 * 参照OpenIM Server的msgtransfer消息路由实现
 * 
 * @author CheeseIM
 */
@Service
public class MessageRouterServiceImpl implements MessageRouterService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageRouterServiceImpl.class);
    
    @Autowired
    private OnlineUserService onlineUserService;
    
    @Autowired
    private GroupMemberService groupMemberService;
    
    @Override
    public RouteResult routeMessage(Message message) {
        try {
            if (message == null) {
                return RouteResult.failure("消息不能为空");
            }
            
            // 验证消息基本信息
            if (message.getSessionType() == null) {
                return RouteResult.failure("会话类型不能为空");
            }
            
            // 根据会话类型路由消息
            switch (message.getSessionType()) {
                case MessageConstants.SESSION_TYPE_SINGLE:
                    return routeSingleChatMessage(message);
                    
                case MessageConstants.SESSION_TYPE_GROUP:
                    return routeGroupChatMessage(message);
                    
                case MessageConstants.SESSION_TYPE_NOTIFICATION:
                    return routeNotificationMessage(message);
                    
                default:
                    return RouteResult.failure("不支持的会话类型: " + message.getSessionType());
            }
            
        } catch (Exception e) {
            logger.error("消息路由失败: serverMsgID={}", message.getServerMsgID(), e);
            return RouteResult.failure("消息路由异常: " + e.getMessage());
        }
    }
    
    @Override
    public RouteResult routeSingleChatMessage(Message message) {
        try {
            String sendID = message.getSendID();
            String recvID = message.getRecvID();
            
            // 验证单聊消息参数
            if (sendID == null || sendID.trim().isEmpty()) {
                return RouteResult.failure("发送者ID不能为空");
            }
            
            if (recvID == null || recvID.trim().isEmpty()) {
                return RouteResult.failure("接收者ID不能为空");
            }
            
            if (sendID.equals(recvID)) {
                return RouteResult.failure("不能给自己发送消息");
            }
            
            // 检查接收者是否存在（这里简化处理，实际应该查询用户服务）
            // TODO: 调用用户服务验证用户是否存在
            
            // 创建目标用户列表
            List<String> targetUsers = new ArrayList<>();
            targetUsers.add(recvID);
            
            // 检查接收者是否在线，决定推送策略
            boolean isReceiverOnline = onlineUserService.isUserOnline(recvID);
            
            RouteResult result = RouteResult.success(RouteStrategy.SINGLE_CHAT, targetUsers);
            result.setNeedPush(isReceiverOnline); // 只有在线用户才需要推送
            result.setNeedStore(true); // 单聊消息都需要存储
            result.setPriority(1); // 单聊消息优先级为1
            
            logger.debug("单聊消息路由成功: sendID={}, recvID={}, online={}", 
                        sendID, recvID, isReceiverOnline);
            
            return result;
            
        } catch (Exception e) {
            logger.error("单聊消息路由失败: serverMsgID={}", message.getServerMsgID(), e);
            return RouteResult.failure("单聊消息路由异常: " + e.getMessage());
        }
    }
    
    @Override
    public RouteResult routeGroupChatMessage(Message message) {
        try {
            String sendID = message.getSendID();
            String groupID = message.getGroupID();
            
            // 验证群聊消息参数
            if (sendID == null || sendID.trim().isEmpty()) {
                return RouteResult.failure("发送者ID不能为空");
            }
            
            if (groupID == null || groupID.trim().isEmpty()) {
                return RouteResult.failure("群组ID不能为空");
            }
            
            // 获取群组成员列表
            List<String> groupMembers = groupMemberService.getGroupMembers(groupID);
            if (groupMembers == null || groupMembers.isEmpty()) {
                return RouteResult.failure("群组不存在或没有成员");
            }
            
            // 验证发送者是否为群组成员
            if (!groupMembers.contains(sendID)) {
                return RouteResult.failure("发送者不是群组成员");
            }
            
            // 移除发送者自己（不需要给自己推送）
            List<String> targetUsers = new ArrayList<>(groupMembers);
            targetUsers.remove(sendID);
            
            if (targetUsers.isEmpty()) {
                logger.warn("群组只有发送者一人，无需推送: groupID={}, sendID={}", groupID, sendID);
                RouteResult result = RouteResult.success(RouteStrategy.GROUP_CHAT, new ArrayList<>());
                result.setNeedPush(false);
                result.setNeedStore(true); // 仍需存储
                return result;
            }
            
            // 检查在线成员数量
            List<String> onlineMembers = onlineUserService.getOnlineUsers(targetUsers);
            
            RouteResult result = RouteResult.success(RouteStrategy.GROUP_CHAT, targetUsers);
            result.setNeedPush(!onlineMembers.isEmpty()); // 有在线成员才推送
            result.setNeedStore(true); // 群聊消息都需要存储
            result.setPriority(2); // 群聊消息优先级为2
            
            logger.debug("群聊消息路由成功: groupID={}, sendID={}, totalMembers={}, onlineMembers={}", 
                        groupID, sendID, targetUsers.size(), onlineMembers.size());
            
            return result;
            
        } catch (Exception e) {
            logger.error("群聊消息路由失败: serverMsgID={}", message.getServerMsgID(), e);
            return RouteResult.failure("群聊消息路由异常: " + e.getMessage());
        }
    }
    
    @Override
    public RouteResult routeNotificationMessage(Message message) {
        try {
            String sendID = message.getSendID();
            String recvID = message.getRecvID();
            
            // 通知消息可以是单播或广播
            List<String> targetUsers = new ArrayList<>();
            
            if (recvID != null && !recvID.trim().isEmpty()) {
                // 单播通知
                targetUsers.add(recvID);
            } else {
                // 广播通知（这里简化处理，实际应该根据通知类型确定目标用户）
                logger.warn("广播通知消息暂不支持: serverMsgID={}", message.getServerMsgID());
                return RouteResult.failure("广播通知消息暂不支持");
            }
            
            // 检查目标用户是否在线
            List<String> onlineUsers = onlineUserService.getOnlineUsers(targetUsers);
            
            RouteResult result = RouteResult.success(RouteStrategy.NOTIFICATION, targetUsers);
            result.setNeedPush(!onlineUsers.isEmpty()); // 有在线用户才推送
            result.setNeedStore(true); // 通知消息需要存储
            result.setPriority(0); // 通知消息优先级最高
            
            logger.debug("通知消息路由成功: sendID={}, recvID={}, onlineUsers={}", 
                        sendID, recvID, onlineUsers.size());
            
            return result;
            
        } catch (Exception e) {
            logger.error("通知消息路由失败: serverMsgID={}", message.getServerMsgID(), e);
            return RouteResult.failure("通知消息路由异常: " + e.getMessage());
        }
    }
}
