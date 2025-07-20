package com.cheeseocean.im.push.listener;

import com.cheeseocean.im.common.constant.MessageConstants;
import com.cheeseocean.im.common.constant.OptionConstants;
import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.common.entity.PushMessageData;
import com.cheeseocean.im.common.util.MessageSerializationUtil;
import com.cheeseocean.im.postoffice.api.OnlinePushService;
import com.cheeseocean.im.postoffice.api.param.OnlinePushResult;
import com.cheeseocean.im.push.entity.OfflinePushResult;
import com.cheeseocean.im.push.service.OfflinePushService;
import com.cheeseocean.im.push.util.PushContentGenerator;
import org.apache.dubbo.common.constants.ClusterRules;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 推送消息监听器
 *
 * @author xxxcrel
 */
@Component
public class PushMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(PushMessageListener.class);

    @DubboReference(cluster = ClusterRules.MERGEABLE)
    private OnlinePushService onlinePushService;

    @Autowired
    private OfflinePushService offlinePushService;

    @KafkaListener(topics = KafkaTopics.PUSH_TOPIC, groupId = "push-service-group")
    public void handlePushMessage(@Payload byte[] messageData,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment acknowledgment) {
        try {
            logger.debug("收到推送消息: topic={}, partition={}, offset={}, dataLength={}",
                    topic, partition, offset, messageData.length);

            // 解析推送消息 - 从byte数组反序列化
            PushMessageData pushData = MessageSerializationUtil.deserialize(messageData, PushMessageData.class);

            if (pushData == null || pushData.getMessage() == null) {
                logger.error("推送消息数据无效: dataLength={}", messageData.length);
                acknowledgment.acknowledge();
                return;
            }
            //TODO: 记录推送耗时

            Message      message     = pushData.getMessage();
            List<String> targetUsers = pushData.getTargetUsers();

            if (targetUsers == null || targetUsers.isEmpty()) {
                logger.warn("推送目标用户为空: messageID={}", message.getServerMsgID());
                acknowledgment.acknowledge();
                return;
            }

            if (message.getSessionType() == MessageConstants.SessionType.READ_GROUP_CHAT_TYPE) {// 写群聊消息，需要根据每个用户的群聊推送配置判断
                //push2GroupUsers(message, targetUsers);
            } else {
                push2Users(message);
            }

            // 手动确认消息
            acknowledgment.acknowledge();

        } catch (Exception e) {
            logger.error("处理推送消息失败: dataLength={}", messageData.length, e);
            // 确认消息以避免重复处理
            acknowledgment.acknowledge();
        }
    }

    private void push2Users(Message message) {
        try {
            long startTime = System.currentTimeMillis();

            logger.info("开始Push2User: messageID={}, sessionType={}, contentType={}",
                    message.getServerMsgID(), message.getSessionType(), message.getContentType());

            List<String> pushUserIds = new ArrayList<>();
            if (!message.getOptions().getOrDefault(OptionConstants.SENDER_SYNC, true) || Objects.equals(message.getSendID(), message.getRecvID())) {
                // 不需要同步发送者, 或者自己给自己发消息
                pushUserIds.add(message.getRecvID());
            } else {
                pushUserIds.add(message.getRecvID());
                pushUserIds.add(message.getSendID());
            }

            // 1. 先进行在线推送（onlinePush）
            OnlinePushResult onlinePushResult = performOnlinePush(message, pushUserIds);

            // 2. 判断消息是否需要推送
            if (!shouldOfflinePushMessage(message)) {
                return;
            }

            for (OnlinePushResult pushResult : onlinePushResult) {
                if (Objects.equals(message.getSendID(), pushResult.getUserId())) {
                    // 发送者不需要推送
                    continue;
                }
                if (pushResult.isSuccess()) {
                    // 在线推送成功，跳过离线推送
                    logger.info("在线推送完全成功，跳过离线推送: messageID={}", message.getServerMsgID());

                    return;
                }
            }

            // 4. 判断是否需要离线推送
            List<String> needOfflinePushUsers = List.of(message.getRecvID());

            // 6. 进行离线推送（offlinePush）
            boolean offlinePushResult = performOfflinePush(message, needOfflinePushUsers);

            long totalTime = System.currentTimeMillis() - startTime;

            logger.info("Push2Users完成: messageID={}, targetUsers={}, onlineSuccess={}, offlinePush={}, offlinePushResult={}, totalTime={}ms",
                    message.getServerMsgID(), pushUserIds, onlinePushResult.isSuccess(),
                    needOfflinePushUsers.size(), offlinePushResult, totalTime);

            // 记录统计
            recordPushStatistics(message, pushUserIds.size(),
                    needOfflinePushUsers.size(), totalTime);

        } catch (Exception e) {
            logger.error("Push2Users异常: messageID={}", message.getServerMsgID(), e);
        }
    }

    /**
     * 判断消息是否需要推送
     */
    private boolean shouldOfflinePushMessage(Message message) {
        return PushContentGenerator.shouldOfflinePush(message);
    }

    /**
     * 执行在线推送
     */
    private OnlinePushResult performOnlinePush(Message message, List<String> targetUsers) {
        try {
            long startTime = System.currentTimeMillis();

            // 调用在线推送服务
            OnlinePushResult result = onlinePushService.pushMessageToUsers(message, targetUsers);

            long responseTime = System.currentTimeMillis() - startTime;

            logger.info("在线推送完成: messageID={}, targetUsers={}, responseTime={}ms",
                    message.getServerMsgID(), targetUsers.size(), responseTime);

            return result;
        } catch (Exception e) {
            logger.error("在线推送异常: messageID={}, targetUsers={}",
                    message.getServerMsgID(), targetUsers.size(), e);
            return OnlinePushResult.failure();
        }
    }

    /**
     * 执行离线推送
     */
    private boolean performOfflinePush(Message message, List<String> needOfflinePushUsers) {
        if (needOfflinePushUsers.isEmpty()) {
            return true;
        }

        try {
            long startTime = System.currentTimeMillis();

            OfflinePushResult result = offlinePushService.pushMessageToUsers(message, needOfflinePushUsers);

            long responseTime = System.currentTimeMillis() - startTime;

            logger.info("离线推送消息已发送: messageID={}, targetUsers={}, responseTime={}ms",
                    message.getServerMsgID(), needOfflinePushUsers.size(), responseTime);

            return true;

        } catch (Exception e) {
            logger.error("离线推送异常: messageID={}, targetUsers={}",
                    message.getServerMsgID(), needOfflinePushUsers.size(), e);
            return false;
        }
    }

    /**
     * 记录推送统计
     */
    private void recordPushStatistics(Message message, int onlineSuccessCount,
                                      int offlinePushCount, long totalTime) {
        try {
            //TODO: 记录统计数据
        } catch (Exception e) {
            logger.error("记录推送统计失败: messageID={}", message.getServerMsgID(), e);
        }
    }


}
