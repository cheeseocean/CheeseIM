package com.cheeseocean.im.push.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.push.entity.OfflinePushResult;
import com.cheeseocean.im.push.service.OfflinePushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 离线推送监听器
 * 监听离线推送Topic，进行第三方推送服务推送
 * 参照OpenIM Server的offlinepush实现
 * 
 * @author CheeseIM
 */
@Component
public class OfflinePushListener {
    
    private static final Logger logger = LoggerFactory.getLogger(OfflinePushListener.class);
    
    @Autowired
    private OfflinePushService offlinePushService;
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 监听离线推送Topic，进行第三方推送
     */
    @KafkaListener(topics = KafkaTopics.OFFLINE_PUSH_TOPIC, groupId = "offline-push-service-group")
    public void handleOfflinePushMessage(@Payload String messageJson,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                        @Header(KafkaHeaders.OFFSET) long offset,
                                        Acknowledgment acknowledgment) {
        try {
            logger.debug("收到离线推送消息: topic={}, partition={}, offset={}, message={}", 
                        topic, partition, offset, messageJson);
            
            // 解析离线推送消息
            OfflinePushMessage offlinePushMessage = objectMapper.readValue(messageJson, OfflinePushMessage.class);
            
            if (offlinePushMessage == null || offlinePushMessage.getMessage() == null) {
                logger.error("离线推送消息数据无效: {}", messageJson);
                acknowledgment.acknowledge();
                return;
            }
            
            Message message = offlinePushMessage.getMessage();
            List<String> targetUsers = offlinePushMessage.getTargetUsers();
            
            if (targetUsers == null || targetUsers.isEmpty()) {
                logger.warn("离线推送目标用户为空: messageID={}", message.getServerMsgID());
                acknowledgment.acknowledge();
                return;
            }
            
            // 执行离线推送
            OfflinePushResult result = offlinePushService.pushMessageToUsers(message, targetUsers);
            
            if (result.isSuccess()) {
                logger.info("离线推送成功: messageID={}, targetUsers={}, successUsers={}, failedUsers={}, totalTime={}ms", 
                           message.getServerMsgID(), targetUsers.size(), 
                           result.getSuccessUsers(), result.getFailedUsers(), result.getTotalResponseTime());
            } else {
                logger.warn("离线推送失败: messageID={}, targetUsers={}, error={}", 
                           message.getServerMsgID(), targetUsers.size(), result.getErrorMessage());
                
                // 检查是否需要重试
                if (offlinePushMessage.canRetry()) {
                    handleRetry(offlinePushMessage, result.getErrorMessage());
                } else {
                    logger.error("离线推送重试次数已达上限: messageID={}, retryCount={}", 
                               message.getServerMsgID(), offlinePushMessage.getRetryCount());
                }
            }
            
            // 手动确认消息
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            logger.error("处理离线推送消息失败: {}", messageJson, e);
            
            try {
                // 尝试解析消息进行重试
                OfflinePushMessage offlinePushMessage = objectMapper.readValue(messageJson, OfflinePushMessage.class);
                if (offlinePushMessage != null && offlinePushMessage.canRetry()) {
                    handleRetry(offlinePushMessage, "处理异常: " + e.getMessage());
                }
            } catch (Exception retryException) {
                logger.error("重试处理也失败: {}", messageJson, retryException);
            }
            
            // 确认消息以避免重复处理
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * 处理重试逻辑
     */
    private void handleRetry(OfflinePushMessage offlinePushMessage, String errorMessage) {
        try {
            offlinePushMessage.incrementRetryCount();
            offlinePushMessage.setErrorMessage(errorMessage);
            offlinePushMessage.setPushTime(System.currentTimeMillis());
            
            // 计算重试延迟（指数退避）
            long retryDelay = calculateRetryDelay(offlinePushMessage.getRetryCount());
            
            logger.info("准备重试离线推送: messageID={}, retryCount={}, delay={}ms", 
                       offlinePushMessage.getMessage().getServerMsgID(), 
                       offlinePushMessage.getRetryCount(), retryDelay);
            
            // 延迟后重新发送到离线推送Topic
            // 这里简化处理，实际应该使用延迟队列或定时任务
            Thread.sleep(retryDelay);
            
            String retryMessageJson = objectMapper.writeValueAsString(offlinePushMessage);
            kafkaTemplate.send(KafkaTopics.OFFLINE_PUSH_TOPIC, 
                             offlinePushMessage.getMessage().getServerMsgID(), 
                             retryMessageJson);
            
            logger.info("离线推送重试消息已发送: messageID={}, retryCount={}", 
                       offlinePushMessage.getMessage().getServerMsgID(), 
                       offlinePushMessage.getRetryCount());
            
        } catch (Exception e) {
            logger.error("处理离线推送重试失败: messageID={}", 
                        offlinePushMessage.getMessage().getServerMsgID(), e);
        }
    }
    
    /**
     * 计算重试延迟（指数退避）
     */
    private long calculateRetryDelay(int retryCount) {
        // 基础延迟1秒，每次重试翻倍，最大30秒
        long baseDelay = 1000; // 1秒
        long delay = baseDelay * (1L << (retryCount - 1));
        return Math.min(delay, 30000); // 最大30秒
    }
    
    /**
     * 离线推送消息类
     */
    public static class OfflinePushMessage {
        private Message message;
        private List<String> targetUsers;
        private Long pushTime;
        private Integer retryCount;
        private Integer maxRetryCount;
        private String errorMessage;
        
        public OfflinePushMessage() {
            this.retryCount = 0;
            this.maxRetryCount = 3;
        }
        
        public OfflinePushMessage(Message message, List<String> targetUsers) {
            this();
            this.message = message;
            this.targetUsers = targetUsers;
            this.pushTime = System.currentTimeMillis();
        }
        
        /**
         * 是否可以重试
         */
        public boolean canRetry() {
            return retryCount < maxRetryCount;
        }
        
        /**
         * 增加重试次数
         */
        public void incrementRetryCount() {
            this.retryCount++;
        }
        
        // Getter and Setter
        public Message getMessage() {
            return message;
        }
        
        public void setMessage(Message message) {
            this.message = message;
        }
        
        public List<String> getTargetUsers() {
            return targetUsers;
        }
        
        public void setTargetUsers(List<String> targetUsers) {
            this.targetUsers = targetUsers;
        }
        
        public Long getPushTime() {
            return pushTime;
        }
        
        public void setPushTime(Long pushTime) {
            this.pushTime = pushTime;
        }
        
        public Integer getRetryCount() {
            return retryCount;
        }
        
        public void setRetryCount(Integer retryCount) {
            this.retryCount = retryCount;
        }
        
        public Integer getMaxRetryCount() {
            return maxRetryCount;
        }
        
        public void setMaxRetryCount(Integer maxRetryCount) {
            this.maxRetryCount = maxRetryCount;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        @Override
        public String toString() {
            return "OfflinePushMessage{" +
                    "message=" + message +
                    ", targetUsers=" + targetUsers +
                    ", pushTime=" + pushTime +
                    ", retryCount=" + retryCount +
                    ", maxRetryCount=" + maxRetryCount +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
}
