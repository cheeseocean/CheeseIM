package com.cheeseocean.im.postbox.listener;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.postbox.service.MessageRouterService;
import com.cheeseocean.im.postbox.service.MessageTransferService;
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
 * 消息传输监听器
 * 参照OpenIM Server的msgtransfer实现
 * 监听toRedisTopic，进行消息路由和分发
 *
 * @author CheeseIM
 */
@Component
public class MessageTransferListener {

    private static final Logger logger = LoggerFactory.getLogger(MessageTransferListener.class);

    @Autowired
    private MessageRouterService messageRouterService;

    @Autowired
    private MessageTransferService messageTransferService;

    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 监听toRedisTopic，处理消息传输
     * 参照OpenIM Server的msgtransfer消息处理流程
     */
    @KafkaListener(topics = KafkaTopics.MSG_TOPIC, groupId = "postman-transfer-group")
    public void handleMessageTransfer(@Payload String messageJson,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset,
                                     Acknowledgment acknowledgment) {
        try {
            logger.info("收到消息传输请求: topic={}, partition={}, offset={}, message={}",
                       topic, partition, offset, messageJson);

            // 解析消息
            Message message = objectMapper.readValue(messageJson, Message.class);

            // 验证消息
            if (message == null || message.getServerMsgID() == null) {
                logger.error("无效的消息数据: {}", messageJson);
                acknowledgment.acknowledge();
                return;
            }

            // 1. 消息路由 - 决定消息分发策略
            MessageRouterService.RouteResult routeResult = messageRouterService.routeMessage(message);
            if (!routeResult.isSuccess()) {
                logger.error("消息路由失败: serverMsgID={}, error={}",
                           message.getServerMsgID(), routeResult.getErrorMessage());
                acknowledgment.acknowledge();
                return;
            }

            logger.info("消息路由成功: serverMsgID={}, strategy={}, targetUsers={}, needPush={}, needStore={}",
                       message.getServerMsgID(), routeResult.getStrategy(),
                       routeResult.getTargetUsers().size(), routeResult.isNeedPush(), routeResult.isNeedStore());

            // 2. 消息传输 - 根据路由结果进行消息分发
            MessageTransferService.TransferResult transferResult =
                messageTransferService.transferMessage(message, routeResult);

            if (transferResult.isSuccess()) {
                logger.info("消息传输完成: serverMsgID={}, pushSent={}, storeSent={}",
                           message.getServerMsgID(), transferResult.isPushSent(), transferResult.isStoreSent());
            } else {
                logger.error("消息传输失败: serverMsgID={}, error={}",
                           message.getServerMsgID(), transferResult.getErrorMessage());
            }

            // 手动确认消息
            acknowledgment.acknowledge();

        } catch (Exception e) {
            logger.error("处理消息传输失败: {}", messageJson, e);
            // 确认消息以避免重复处理
            acknowledgment.acknowledge();
        }
    }
}
