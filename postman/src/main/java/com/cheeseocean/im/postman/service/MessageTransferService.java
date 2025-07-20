package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.entity.Message;

/**
 * 消息传输服务接口
 * 参照OpenIM Server的msgtransfer消息传输功能
 * 
 * @author CheeseIM
 */
public interface MessageTransferService {
    
    /**
     * 传输消息
     * 根据路由结果进行消息分发
     * 
     * @param message 消息对象
     * @param routeResult 路由结果
     * @return 传输结果
     */
    TransferResult transferMessage(Message message, MessageRouterService.RouteResult routeResult);
    
    /**
     * 发送消息到推送Topic
     * 
     * @param message 消息对象
     * @param targetUsers 目标用户列表
     * @return 是否发送成功
     */
    boolean sendToPushTopic(Message message, java.util.List<String> targetUsers);
    
    /**
     * 发送消息到存储Topic
     * 
     * @param message 消息对象
     * @return 是否发送成功
     */
    boolean sendToStorageTopic(Message message);
    
    /**
     * 发送消息状态更新
     * 
     * @param message 消息对象
     * @param status 消息状态
     * @return 是否发送成功
     */
    boolean sendMessageStatusUpdate(Message message, String status);
    
    /**
     * 批量传输消息
     * 
     * @param messages 消息列表
     * @return 传输结果列表
     */
    java.util.List<TransferResult> batchTransferMessages(java.util.List<Message> messages);
    
    /**
     * 传输结果类
     */
    class TransferResult {
        private boolean success;
        private String errorMessage;
        private boolean pushSent;
        private boolean storeSent;
        private boolean statusUpdated;
        private int targetUserCount;
        private long transferTime;
        
        public TransferResult() {
            this.transferTime = System.currentTimeMillis();
        }
        
        public TransferResult(boolean success) {
            this();
            this.success = success;
        }
        
        public static TransferResult success() {
            return new TransferResult(true);
        }
        
        public static TransferResult success(boolean pushSent, boolean storeSent) {
            TransferResult result = new TransferResult(true);
            result.setPushSent(pushSent);
            result.setStoreSent(storeSent);
            return result;
        }
        
        public static TransferResult failure(String errorMessage) {
            TransferResult result = new TransferResult(false);
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
        
        public boolean isPushSent() {
            return pushSent;
        }
        
        public void setPushSent(boolean pushSent) {
            this.pushSent = pushSent;
        }
        
        public boolean isStoreSent() {
            return storeSent;
        }
        
        public void setStoreSent(boolean storeSent) {
            this.storeSent = storeSent;
        }
        
        public boolean isStatusUpdated() {
            return statusUpdated;
        }
        
        public void setStatusUpdated(boolean statusUpdated) {
            this.statusUpdated = statusUpdated;
        }
        
        public int getTargetUserCount() {
            return targetUserCount;
        }
        
        public void setTargetUserCount(int targetUserCount) {
            this.targetUserCount = targetUserCount;
        }
        
        public long getTransferTime() {
            return transferTime;
        }
        
        public void setTransferTime(long transferTime) {
            this.transferTime = transferTime;
        }
        
        @Override
        public String toString() {
            return "TransferResult{" +
                    "success=" + success +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", pushSent=" + pushSent +
                    ", storeSent=" + storeSent +
                    ", statusUpdated=" + statusUpdated +
                    ", targetUserCount=" + targetUserCount +
                    ", transferTime=" + transferTime +
                    '}';
        }
    }
}
