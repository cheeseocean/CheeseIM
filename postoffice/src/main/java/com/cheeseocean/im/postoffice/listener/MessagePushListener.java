package com.cheeseocean.im.postoffice.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.protocol.WSMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 消息推送监听器
 * 监听Kafka中的推送消息，并实时推送给在线用户
 * 
 * @author CheeseIM
 */
@Component
public class MessagePushListener {
    
    private static final Logger logger = LoggerFactory.getLogger(MessagePushListener.class);
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 监听推送Topic，接收需要推送给用户的消息
     */
    @KafkaListener(topics = KafkaTopics.PUSH_TOPIC, groupId = "postoffice-push-group")
    public void handlePushMessage(@Payload String messageJson,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 Acknowledgment acknowledgment) {
        try {
            logger.debug("Received push message from topic: {}, partition: {}, offset: {}", 
                        topic, partition, offset);
            
            // 解析消息
            Message message = objectMapper.readValue(messageJson, Message.class);
            
            // 推送消息给接收者
            pushMessageToReceiver(message);
            
            // 手动确认消息
            acknowledgment.acknowledge();
            
            logger.debug("Push message processed successfully: serverMsgID={}, recvID={}", 
                        message.getServerMsgID(), message.getRecvID());
            
        } catch (Exception e) {
            logger.error("Failed to process push message: {}", messageJson, e);
            // 这里可以根据业务需求决定是否确认消息
            // 如果不确认，消息会重新投递
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * 监听用户状态Topic，处理用户上下线状态变更
     */
    @KafkaListener(topics = KafkaTopics.USER_ONLINE_STATUS_TOPIC, groupId = "postoffice-status-group")
    public void handleUserStatusMessage(@Payload String messageJson,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       Acknowledgment acknowledgment) {
        try {
            logger.debug("Received user status message from topic: {}", topic);
            
            // 解析用户状态消息
            UserStatusMessage statusMessage = objectMapper.readValue(messageJson, UserStatusMessage.class);
            
            // 处理用户状态变更
            handleUserStatusChange(statusMessage);
            
            // 手动确认消息
            acknowledgment.acknowledge();
            
            logger.debug("User status message processed: userID={}, status={}", 
                        statusMessage.getUserID(), statusMessage.getStatus());
            
        } catch (Exception e) {
            logger.error("Failed to process user status message: {}", messageJson, e);
            acknowledgment.acknowledge();
        }
    }
    
    /**
     * 推送消息给接收者
     */
    private void pushMessageToReceiver(Message message) {
        try {
            String recvID = message.getRecvID();
            
            // 根据会话类型确定接收者
            if (message.getSessionType() == 1) {
                // 单聊消息，推送给接收者
                pushToUser(recvID, message);
            } else if (message.getSessionType() == 2) {
                // 群聊消息，推送给群组成员（这里简化处理，实际需要查询群组成员列表）
                pushToGroup(message.getGroupID(), message);
            }
            
        } catch (Exception e) {
            logger.error("Failed to push message to receiver: serverMsgID={}", 
                        message.getServerMsgID(), e);
        }
    }
    
    /**
     * 推送消息给指定用户
     */
    private void pushToUser(String userID, Message message) {
        try {
            // 检查用户是否在线
            if (!connectionManager.isUserOnline(userID)) {
                logger.debug("User is offline, skip push: userID={}, serverMsgID={}", 
                           userID, message.getServerMsgID());
                return;
            }
            
            // 创建接收消息通知
            WSMessage recvMsgNotify = WSMessage.recvMsgNotify("system", message);
            
            // 推送给用户的所有连接
            int successCount = connectionManager.sendMessageToUser(userID, recvMsgNotify);
            
            logger.info("Message pushed to user: userID={}, serverMsgID={}, connections={}", 
                       userID, message.getServerMsgID(), successCount);
            
        } catch (Exception e) {
            logger.error("Failed to push message to user: userID={}, serverMsgID={}", 
                        userID, message.getServerMsgID(), e);
        }
    }
    
    /**
     * 推送消息给群组成员
     */
    private void pushToGroup(String groupID, Message message) {
        try {
            // TODO: 这里需要查询群组成员列表，然后推送给每个在线成员
            // 为了简化，这里暂时不实现群组推送逻辑
            logger.debug("Group message push not implemented yet: groupID={}, serverMsgID={}", 
                        groupID, message.getServerMsgID());
            
        } catch (Exception e) {
            logger.error("Failed to push message to group: groupID={}, serverMsgID={}", 
                        groupID, message.getServerMsgID(), e);
        }
    }
    
    /**
     * 处理用户状态变更
     */
    private void handleUserStatusChange(UserStatusMessage statusMessage) {
        try {
            String userID = statusMessage.getUserID();
            String status = statusMessage.getStatus();
            Integer platformID = statusMessage.getPlatformID();
            
            WSMessage statusNotify;
            if ("online".equals(status)) {
                statusNotify = WSMessage.userOnlineNotify("system", userID, platformID);
            } else if ("offline".equals(status)) {
                statusNotify = WSMessage.userOfflineNotify("system", userID, platformID);
            } else {
                // 其他状态变更
                statusNotify = new WSMessage(WSMessageType.WS_USER_STATUS_CHANGE_NOTIFY, 
                                           "system", statusMessage);
            }
            
            // 广播状态变更通知（实际应该只通知相关用户，如好友）
            // 这里简化处理，不进行广播
            logger.debug("User status change handled: userID={}, status={}, platformID={}", 
                        userID, status, platformID);
            
        } catch (Exception e) {
            logger.error("Failed to handle user status change: {}", statusMessage, e);
        }
    }
    
    /**
     * 用户状态消息实体
     */
    public static class UserStatusMessage {
        private String userID;
        private String status;
        private Integer platformID;
        private Long timestamp;
        
        public UserStatusMessage() {}
        
        public UserStatusMessage(String userID, String status, Integer platformID) {
            this.userID = userID;
            this.status = status;
            this.platformID = platformID;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getter and Setter methods
        public String getUserID() {
            return userID;
        }
        
        public void setUserID(String userID) {
            this.userID = userID;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public Integer getPlatformID() {
            return platformID;
        }
        
        public void setPlatformID(Integer platformID) {
            this.platformID = platformID;
        }
        
        public Long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }
        
        @Override
        public String toString() {
            return "UserStatusMessage{" +
                    "userID='" + userID + '\'' +
                    ", status='" + status + '\'' +
                    ", platformID=" + platformID +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
