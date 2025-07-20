package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.entity.Message;

/**
 * 消息路由服务接口
 * 参照OpenIM Server的msgtransfer消息路由功能
 * 
 * @author CheeseIM
 */
public interface MessageRouterService {
    
    /**
     * 路由消息
     * 根据消息类型和接收者信息，决定消息的分发策略
     * 
     * @param message 消息对象
     * @return 路由结果
     */
    RouteResult routeMessage(Message message);
    
    /**
     * 路由单聊消息
     * 
     * @param message 单聊消息
     * @return 路由结果
     */
    RouteResult routeSingleChatMessage(Message message);
    
    /**
     * 路由群聊消息
     * 
     * @param message 群聊消息
     * @return 路由结果
     */
    RouteResult routeGroupChatMessage(Message message);
    
    /**
     * 路由通知消息
     * 
     * @param message 通知消息
     * @return 路由结果
     */
    RouteResult routeNotificationMessage(Message message);
    
    /**
     * 路由结果类
     */
    class RouteResult {
        private boolean success;
        private String errorMessage;
        private RouteStrategy strategy;
        private java.util.List<String> targetUsers;
        private boolean needPush;
        private boolean needStore;
        private int priority;
        
        public RouteResult() {}
        
        public RouteResult(boolean success) {
            this.success = success;
        }
        
        public static RouteResult success(RouteStrategy strategy, java.util.List<String> targetUsers) {
            RouteResult result = new RouteResult(true);
            result.setStrategy(strategy);
            result.setTargetUsers(targetUsers);
            result.setNeedPush(true);
            result.setNeedStore(true);
            result.setPriority(1);
            return result;
        }
        
        public static RouteResult failure(String errorMessage) {
            RouteResult result = new RouteResult(false);
            result.setErrorMessage(errorMessage);
            return result;
        }
        
        // Getter and Setter
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        public RouteStrategy getStrategy() {
            return strategy;
        }
        
        public void setStrategy(RouteStrategy strategy) {
            this.strategy = strategy;
        }
        
        public java.util.List<String> getTargetUsers() {
            return targetUsers;
        }
        
        public void setTargetUsers(java.util.List<String> targetUsers) {
            this.targetUsers = targetUsers;
        }
        
        public boolean isNeedPush() {
            return needPush;
        }
        
        public void setNeedPush(boolean needPush) {
            this.needPush = needPush;
        }
        
        public boolean isNeedStore() {
            return needStore;
        }
        
        public void setNeedStore(boolean needStore) {
            this.needStore = needStore;
        }
        
        public int getPriority() {
            return priority;
        }
        
        public void setPriority(int priority) {
            this.priority = priority;
        }
        
        @Override
        public String toString() {
            return "RouteResult{" +
                    "success=" + success +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", strategy=" + strategy +
                    ", targetUsers=" + targetUsers +
                    ", needPush=" + needPush +
                    ", needStore=" + needStore +
                    ", priority=" + priority +
                    '}';
        }
    }
    
    /**
     * 路由策略枚举
     */
    enum RouteStrategy {
        SINGLE_CHAT,        // 单聊路由
        GROUP_CHAT,         // 群聊路由
        BROADCAST,          // 广播路由
        NOTIFICATION,       // 通知路由
        SYSTEM_MESSAGE      // 系统消息路由
    }
}
