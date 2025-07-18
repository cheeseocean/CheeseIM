package com.cheeseocean.im.postman.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.entity.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 消息传输监听器
 * 监听toRedisTopic，将消息分发到toPushTopic和toMongoTopic
 * 
 * @author CheeseIM
 */
@Component
public class MessageTransferListener {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageTransferListener.class);
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 监听toRedisTopic，处理消息传输
     */
    @KafkaListener(topics = KafkaTopics.TO_REDIS_TOPIC, groupId = "postman-transfer-group")
    public void handleMessageTransfer(String messageJson) {
        try {
            logger.info("收到消息传输请求: {}", messageJson);
            
            // 解析消息
            Message message = objectMapper.readValue(messageJson, Message.class);
            
            // 验证消息
            if (message == null || message.getServerMsgID() == null) {
                logger.error("无效的消息数据: {}", messageJson);
                return;
            }
            
            // 1. 发送到推送Topic (toPushTopic)
            // 用于实时推送给在线用户
            kafkaTemplate.send(KafkaTopics.TO_PUSH_TOPIC, message.getServerMsgID(), messageJson);
            logger.info("消息已发送到推送Topic: {}, msgID: {}", KafkaTopics.TO_PUSH_TOPIC, message.getServerMsgID());
            
            // 2. 发送到MongoDB存储Topic (toMongoTopic)  
            // 用于消息持久化存储
            kafkaTemplate.send(KafkaTopics.TO_MONGO_TOPIC, message.getServerMsgID(), messageJson);
            logger.info("消息已发送到存储Topic: {}, msgID: {}", KafkaTopics.TO_MONGO_TOPIC, message.getServerMsgID());
            
            logger.info("消息传输完成, msgID: {}", message.getServerMsgID());
            
        } catch (Exception e) {
            logger.error("处理消息传输失败: {}", messageJson, e);
        }
    }
}
