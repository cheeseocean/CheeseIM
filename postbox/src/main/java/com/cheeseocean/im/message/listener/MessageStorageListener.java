package com.cheeseocean.im.message.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.message.service.MessageStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 消息存储监听器
 * 监听toMongoTopic，将消息存储到MongoDB
 * 
 * @author CheeseIM
 */
@Component
public class MessageStorageListener {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageStorageListener.class);
    
    @Autowired
    private MessageStorageService messageStorageService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 监听toMongoTopic，处理消息存储
     */
    @KafkaListener(topics = KafkaTopics.TO_MONGO_TOPIC, groupId = "message-storage-group")
    public void handleMessageStorage(String messageJson) {
        try {
            logger.info("收到消息存储请求: {}", messageJson);
            
            // 解析消息
            Message message = objectMapper.readValue(messageJson, Message.class);
            
            // 验证消息
            if (message == null || message.getServerMsgID() == null) {
                logger.error("无效的消息数据: {}", messageJson);
                return;
            }
            
            // 存储消息到MongoDB
            messageStorageService.saveMessage(message);
            
            logger.info("消息存储完成: serverMsgID={}", message.getServerMsgID());
            
        } catch (Exception e) {
            logger.error("处理消息存储失败: {}", messageJson, e);
        }
    }
}
