package com.cheeseocean.im.postbox.service.impl;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.postbox.service.MessageRouterService;
import com.cheeseocean.im.postbox.service.MessageTransferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息传输服务实现
 * 参照OpenIM Server的msgtransfer消息传输实现
 * 
 * @author CheeseIM
 */
@Service
public class MessageTransferServiceImpl implements MessageTransferService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageTransferServiceImpl.class);
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public TransferResult transferMessage(Message message, MessageRouterService.RouteResult routeResult) {
        try {
            if (message == null || routeResult == null) {
                return TransferResult.failure("消息或路由结果不能为空");
            }
            
            if (!routeResult.isSuccess()) {
                return TransferResult.failure("路由失败: " + routeResult.getErrorMessage());
            }
            
            boolean pushSent = false;
            boolean storeSent = false;
            
            // 1. 发送到推送Topic（如果需要推送）
            if (routeResult.isNeedPush() && routeResult.getTargetUsers() != null && !routeResult.getTargetUsers().isEmpty()) {
                pushSent = sendToPushTopic(message, routeResult.getTargetUsers());
                if (!pushSent) {
                    logger.warn("推送消息发送失败: serverMsgID={}", message.getServerMsgID());
                }
            } else {
                logger.debug("消息无需推送: serverMsgID={}, needPush={}, targetUsers={}", 
                           message.getServerMsgID(), routeResult.isNeedPush(), 
                           routeResult.getTargetUsers() != null ? routeResult.getTargetUsers().size() : 0);
                pushSent = true; // 不需要推送时标记为成功
            }
            
            // 2. 发送到存储Topic（如果需要存储）
            if (routeResult.isNeedStore()) {
                storeSent = sendToStorageTopic(message);
                if (!storeSent) {
                    logger.warn("存储消息发送失败: serverMsgID={}", message.getServerMsgID());
                }
            } else {
                logger.debug("消息无需存储: serverMsgID={}", message.getServerMsgID());
                storeSent = true; // 不需要存储时标记为成功
            }
            
            // 3. 发送消息状态更新
            boolean statusUpdated = sendMessageStatusUpdate(message, "transferred");
            
            TransferResult result = TransferResult.success(pushSent, storeSent);
            result.setStatusUpdated(statusUpdated);
            result.setTargetUserCount(routeResult.getTargetUsers() != null ? routeResult.getTargetUsers().size() : 0);
            
            // 如果推送或存储失败，整体标记为失败
            if (!pushSent || !storeSent) {
                result.setSuccess(false);
                result.setErrorMessage("部分传输失败: pushSent=" + pushSent + ", storeSent=" + storeSent);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("消息传输异常: serverMsgID={}", message.getServerMsgID(), e);
            return TransferResult.failure("消息传输异常: " + e.getMessage());
        }
    }
    
    @Override
    public boolean sendToPushTopic(Message message, List<String> targetUsers) {
        try {
            if (message == null || targetUsers == null || targetUsers.isEmpty()) {
                logger.warn("推送消息参数无效: message={}, targetUsers={}", 
                           message != null ? message.getServerMsgID() : null, 
                           targetUsers != null ? targetUsers.size() : 0);
                return false;
            }
            
            // 创建推送消息，包含目标用户信息
            Map<String, Object> pushMessage = new HashMap<>();
            pushMessage.put("message", message);
            pushMessage.put("targetUsers", targetUsers);
            pushMessage.put("pushTime", System.currentTimeMillis());
            
            String pushMessageJson = objectMapper.writeValueAsString(pushMessage);
            
            // 发送到推送Topic
            CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(KafkaTopics.PUSH_TOPIC, message.getServerMsgID(), pushMessageJson);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.debug("推送消息发送成功: serverMsgID={}, topic={}, partition={}, offset={}", 
                               message.getServerMsgID(), 
                               result.getRecordMetadata().topic(),
                               result.getRecordMetadata().partition(),
                               result.getRecordMetadata().offset());
                } else {
                    logger.error("推送消息发送失败: serverMsgID={}", message.getServerMsgID(), ex);
                }
            });
            
            logger.info("推送消息已发送: serverMsgID={}, targetUsers={}", 
                       message.getServerMsgID(), targetUsers.size());
            
            return true;
            
        } catch (Exception e) {
            logger.error("发送推送消息失败: serverMsgID={}", message.getServerMsgID(), e);
            return false;
        }
    }
    
    @Override
    public boolean sendToStorageTopic(Message message) {
        try {
            if (message == null) {
                logger.warn("存储消息参数无效");
                return false;
            }
            
            String messageJson = objectMapper.writeValueAsString(message);
            
            // 发送到存储Topic
            CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(KafkaTopics.PERSISTENT_TOPIC, message.getServerMsgID(), messageJson);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.debug("存储消息发送成功: serverMsgID={}, topic={}, partition={}, offset={}", 
                               message.getServerMsgID(), 
                               result.getRecordMetadata().topic(),
                               result.getRecordMetadata().partition(),
                               result.getRecordMetadata().offset());
                } else {
                    logger.error("存储消息发送失败: serverMsgID={}", message.getServerMsgID(), ex);
                }
            });
            
            logger.info("存储消息已发送: serverMsgID={}", message.getServerMsgID());
            
            return true;
            
        } catch (Exception e) {
            logger.error("发送存储消息失败: serverMsgID={}", message.getServerMsgID(), e);
            return false;
        }
    }
    
    @Override
    public boolean sendMessageStatusUpdate(Message message, String status) {
        try {
            if (message == null || status == null) {
                logger.warn("消息状态更新参数无效");
                return false;
            }
            
            // 创建状态更新消息
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("serverMsgID", message.getServerMsgID());
            statusUpdate.put("clientMsgID", message.getClientMsgID());
            statusUpdate.put("sendID", message.getSendID());
            statusUpdate.put("recvID", message.getRecvID());
            statusUpdate.put("groupID", message.getGroupID());
            statusUpdate.put("status", status);
            statusUpdate.put("updateTime", System.currentTimeMillis());
            
            String statusUpdateJson = objectMapper.writeValueAsString(statusUpdate);
            
            // 发送到状态更新Topic
            CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(KafkaTopics.MSG_STATUS_UPDATE_TOPIC, message.getServerMsgID(), statusUpdateJson);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    logger.debug("状态更新消息发送成功: serverMsgID={}, status={}", 
                               message.getServerMsgID(), status);
                } else {
                    logger.error("状态更新消息发送失败: serverMsgID={}, status={}", 
                               message.getServerMsgID(), status, ex);
                }
            });
            
            logger.debug("状态更新消息已发送: serverMsgID={}, status={}", message.getServerMsgID(), status);
            
            return true;
            
        } catch (Exception e) {
            logger.error("发送状态更新消息失败: serverMsgID={}, status={}", 
                        message.getServerMsgID(), status, e);
            return false;
        }
    }
    
    @Override
    public List<TransferResult> batchTransferMessages(List<Message> messages) {
        List<TransferResult> results = new ArrayList<>();
        
        if (messages == null || messages.isEmpty()) {
            return results;
        }
        
        for (Message message : messages) {
            // 这里简化处理，实际应该批量优化
            // TODO: 实现真正的批量传输优化
            TransferResult result = TransferResult.failure("批量传输暂未实现");
            results.add(result);
        }
        
        return results;
    }
}
