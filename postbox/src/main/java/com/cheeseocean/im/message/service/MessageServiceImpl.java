package com.cheeseocean.im.message.service;

import com.cheeseocean.im.common.constants.KafkaTopics;
import com.cheeseocean.im.common.constants.MessageConstants;
import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.common.service.MessageService;
import com.cheeseocean.im.common.utils.IdGenerator;
import com.cheeseocean.im.message.entity.MessageMongo;
import com.cheeseocean.im.message.utils.ConversationUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 消息服务实现类
 *
 * @author CheeseIM
 */
@Service
@DubboService(version = "1.0.0", timeout = 10000)
public class MessageServiceImpl implements MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageServiceImpl.class);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageStorageService messageStorageService;

    @Override
    public SendMsgResp sendMsg(SendMsgReq request) {
        try {
            logger.info("收到发送消息请求: {}", request);

            // 参数校验
            if (request == null || request.getMsgData() == null) {
                return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "消息数据不能为空");
            }

            Message message = request.getMsgData();

            // 参数校验
            SendMsgResp validationResult = validateMessage(message);
            if (validationResult != null) {
                return validationResult;
            }

            // 生成服务端消息ID
            String serverMsgID = IdGenerator.generateMsgId();
            message.setServerMsgID(serverMsgID);

            // 设置发送时间
            long sendTime = System.currentTimeMillis();
            message.setSendTime(sendTime);
            message.setCreateTime(sendTime);

            // 设置消息状态为发送中
            message.setStatus(MessageConstants.MSG_STATUS_SENDING);

            // 生成会话ID和序列号
            String conversationID = ConversationUtils.generateConversationID(
                    message.getSessionType(), message.getSendID(), message.getRecvID(), message.getGroupID());
            Long seq = messageStorageService.generateSeq(conversationID);
            message.setSeq(seq);

            // 发送消息到Kafka toRedisTopic
            String messageJson = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(KafkaTopics.MSG_TOPIC, message.getClientMsgID(), messageJson);

            logger.info("消息已发送到Kafka topic: {}, msgID: {}", KafkaTopics.MSG_TOPIC, serverMsgID);

            // 返回成功响应
            return SendMsgResp.success(serverMsgID, message.getClientMsgID(), sendTime);

        } catch (JsonProcessingException e) {
            logger.error("消息序列化失败", e);
            return SendMsgResp.error(MessageConstants.ERR_CODE_INTERNAL_ERROR, "消息序列化失败");
        } catch (Exception e) {
            logger.error("发送消息失败", e);
            return SendMsgResp.error(MessageConstants.ERR_CODE_MSG_SEND_FAILED, "发送消息失败: " + e.getMessage());
        }
    }

    @Override
    public SendMsgResp[] batchSendMsg(SendMsgReq[] requests) {
        if (requests == null || requests.length == 0) {
            return new SendMsgResp[0];
        }

        SendMsgResp[] responses = new SendMsgResp[requests.length];
        for (int i = 0; i < requests.length; i++) {
            responses[i] = sendMsg(requests[i]);
        }

        return responses;
    }

    /**
     * 校验消息参数
     */
    private SendMsgResp validateMessage(Message message) {
        // 基础字段校验
        if (StringUtils.isBlank(message.getSendID())) {
            return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "发送者ID不能为空");
        }

        if (StringUtils.isBlank(message.getContent())) {
            return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "消息内容不能为空");
        }

        if (message.getContentType() == null) {
            return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "消息类型不能为空");
        }

        if (message.getSessionType() == null) {
            return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "会话类型不能为空");
        }

        // 根据会话类型校验接收者
        switch (message.getSessionType()) {
            case MessageConstants.SESSION_TYPE_SINGLE:
                if (StringUtils.isBlank(message.getRecvID())) {
                    return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "单聊消息接收者ID不能为空");
                }
                if (message.getSendID().equals(message.getRecvID())) {
                    return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "不能给自己发送消息");
                }
                break;

            case MessageConstants.SESSION_TYPE_GROUP:
                if (StringUtils.isBlank(message.getGroupID())) {
                    return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "群聊消息群组ID不能为空");
                }
                break;

            case MessageConstants.SESSION_TYPE_NOTIFICATION:
                if (StringUtils.isBlank(message.getRecvID())) {
                    return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "通知消息接收者ID不能为空");
                }
                break;

            default:
                return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "不支持的会话类型: " + message.getSessionType());
        }

        // 内容长度校验
        if (message.getContentType() == MessageConstants.CONTENT_TYPE_TEXT &&
                message.getContent().length() > 4096) {
            return SendMsgResp.error(MessageConstants.ERR_CODE_INVALID_PARAM, "文本消息内容过长，最大支持4096字符");
        }

        return null; // 校验通过
    }

    @Override
    public List<Message> getConversationHistory(String conversationID, Long startSeq, Integer count) {
        try {
            if (StringUtils.isBlank(conversationID)) {
                throw new IllegalArgumentException("会话ID不能为空");
            }

            if (count == null || count <= 0) {
                count = 20; // 默认20条
            }

            if (count > 100) {
                count = 100; // 最大100条
            }

            List<MessageMongo> mongoMessages;
            if (startSeq != null && startSeq > 0) {
                // 从指定序列号开始获取
                Long endSeq = startSeq + count - 1;
                mongoMessages = messageStorageService.getMessagesBySeqRange(conversationID, startSeq, endSeq);
            } else {
                // 获取最新消息
                Pageable           pageable = PageRequest.of(0, count);
                Page<MessageMongo> page     = messageStorageService.getConversationHistory(conversationID, pageable);
                mongoMessages = page.getContent();
            }

            return convertToMessages(mongoMessages);

        } catch (Exception e) {
            logger.error("获取会话消息历史失败: conversationID={}", conversationID, e);
            throw new RuntimeException("获取会话消息历史失败", e);
        }
    }

    @Override
    public List<Message> getSingleChatHistory(String userID1, String userID2, Long startSeq, Integer count) {
        try {
            if (StringUtils.isBlank(userID1) || StringUtils.isBlank(userID2)) {
                throw new IllegalArgumentException("用户ID不能为空");
            }

            if (count == null || count <= 0) {
                count = 20;
            }

            if (count > 100) {
                count = 100;
            }

            Pageable           pageable = PageRequest.of(0, count);
            Page<MessageMongo> page     = messageStorageService.getSingleChatHistory(userID1, userID2, pageable);

            return convertToMessages(page.getContent());

        } catch (Exception e) {
            logger.error("获取单聊消息历史失败: userID1={}, userID2={}", userID1, userID2, e);
            throw new RuntimeException("获取单聊消息历史失败", e);
        }
    }

    @Override
    public List<Message> getGroupChatHistory(String groupID, Long startSeq, Integer count) {
        try {
            if (StringUtils.isBlank(groupID)) {
                throw new IllegalArgumentException("群组ID不能为空");
            }

            if (count == null || count <= 0) {
                count = 20;
            }

            if (count > 100) {
                count = 100;
            }

            Pageable           pageable = PageRequest.of(0, count);
            Page<MessageMongo> page     = messageStorageService.getGroupChatHistory(groupID, pageable);

            return convertToMessages(page.getContent());

        } catch (Exception e) {
            logger.error("获取群聊消息历史失败: groupID={}", groupID, e);
            throw new RuntimeException("获取群聊消息历史失败", e);
        }
    }

    @Override
    public List<Message> searchMessages(String userID, String keyword, Integer page, Integer size) {
        try {
            if (StringUtils.isBlank(userID) || StringUtils.isBlank(keyword)) {
                throw new IllegalArgumentException("用户ID和关键词不能为空");
            }

            if (page == null || page < 0) {
                page = 0;
            }

            if (size == null || size <= 0) {
                size = 20;
            }

            if (size > 100) {
                size = 100;
            }

            Pageable           pageable  = PageRequest.of(page, size);
            Page<MessageMongo> mongoPage = messageStorageService.searchUserMessages(userID, keyword, pageable);

            return convertToMessages(mongoPage.getContent());

        } catch (Exception e) {
            logger.error("搜索消息失败: userID={}, keyword={}", userID, keyword, e);
            throw new RuntimeException("搜索消息失败", e);
        }
    }

    @Override
    public Boolean markMessagesAsRead(String userID, List<String> serverMsgIDs) {
        try {
            if (StringUtils.isBlank(userID) || serverMsgIDs == null || serverMsgIDs.isEmpty()) {
                throw new IllegalArgumentException("用户ID和消息ID列表不能为空");
            }

            // 验证消息是否属于该用户
            for (String serverMsgID : serverMsgIDs) {
                Optional<MessageMongo> messageOpt = messageStorageService.findByServerMsgID(serverMsgID);
                if (messageOpt.isPresent()) {
                    MessageMongo message = messageOpt.get();
                    if (!userID.equals(message.getRecvID())) {
                        logger.warn("用户{}尝试标记不属于自己的消息为已读: {}", userID, serverMsgID);
                        continue;
                    }
                }
            }

            messageStorageService.markMessagesAsRead(serverMsgIDs);
            return true;

        } catch (Exception e) {
            logger.error("标记消息为已读失败: userID={}", userID, e);
            return false;
        }
    }

    @Override
    public Boolean revokeMessage(String userID, String serverMsgID) {
        try {
            if (StringUtils.isBlank(userID) || StringUtils.isBlank(serverMsgID)) {
                throw new IllegalArgumentException("用户ID和消息ID不能为空");
            }

            Optional<MessageMongo> messageOpt = messageStorageService.findByServerMsgID(serverMsgID);
            if (!messageOpt.isPresent()) {
                logger.warn("消息不存在: {}", serverMsgID);
                return false;
            }

            MessageMongo message = messageOpt.get();
            if (!userID.equals(message.getSendID())) {
                logger.warn("用户{}尝试撤回不属于自己的消息: {}", userID, serverMsgID);
                return false;
            }

            // 检查撤回时间限制（2分钟内）
            long currentTime = System.currentTimeMillis();
            long sendTime    = message.getSendTime();
            if (currentTime - sendTime > 2 * 60 * 1000) {
                logger.warn("消息发送时间超过2分钟，无法撤回: {}", serverMsgID);
                return false;
            }

            // 更新消息内容为撤回提示
            message.setContent("[消息已撤回]");
            message.setContentType(MessageConstants.CONTENT_TYPE_TEXT);
            // 这里应该调用存储服务的更新方法，暂时用删除代替
            messageStorageService.deleteMessage(serverMsgID);

            return true;

        } catch (Exception e) {
            logger.error("撤回消息失败: userID={}, serverMsgID={}", userID, serverMsgID, e);
            return false;
        }
    }

    /**
     * 转换MongoDB消息为通用消息格式
     */
    private List<Message> convertToMessages(List<MessageMongo> mongoMessages) {
        return mongoMessages.stream().map(this::convertToMessage).collect(Collectors.toList());
    }

    /**
     * 转换单个MongoDB消息为通用消息格式
     */
    private Message convertToMessage(MessageMongo mongoMessage) {
        Message message = new Message();
        BeanUtils.copyProperties(mongoMessage, message);
        return message;
    }
}
