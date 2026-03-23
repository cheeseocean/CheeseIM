package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.dto.message.ChatSendRequest;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.ReadReceiptPayload;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.dto.receipt.ReceiptAckReq;
import com.cheeseocean.im.common.api.rpc.MessageSendRpc;
import com.cheeseocean.im.common.api.rpc.ReceiptAckRpc;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.common.core.enums.ContentType;
import com.cheeseocean.im.common.core.enums.ReceiptType;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.service.MessageSendReqMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 聊天消息处理器
 * 处理客户端发送的聊天消息，调用postbox服务进行消息处理
 * 
 * @author CheeseIM
 */
@Component
public class ChatMessageHandler implements MessageHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatMessageHandler.class);
    
    @DubboReference(check = false)
    private MessageSendRpc messageSendRpc;

    @DubboReference(check = false)
    private ReceiptAckRpc receiptAckRpc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageSendReqMapper messageSendReqMapper;

    @Autowired
    private ConnectionSessionGuard connectionSessionGuard;
    
    @Override
    public HandleResult handle(UserConnection connection, ClientEnvelope envelope) {
        try {
            String operationID = envelope.getRequestId();
            
            // 检查用户是否已认证
            if (!connection.isAuthenticated()) {
                WSMessage errorResp = WSMessage.permissionError(operationID, "用户未认证，无法发送消息");
                return HandleResult.failure("用户未认证", errorResp);
            }

            ConnectionContext context = connection.getContext();
            if (context == null || !context.isAuthenticated()) {
                WSMessage errorResp = WSMessage.permissionError(operationID, "连接上下文无效");
                return HandleResult.failure("连接上下文无效", errorResp);
            }

            connectionSessionGuard.ensureValid(connection);
            
            // 检查消息数据
            if (envelope.getBody() == null) {
                WSMessage errorResp = WSMessage.paramError(operationID, "消息数据不能为空");
                return HandleResult.failure("消息数据不能为空", errorResp);
            }
            
            // 解析消息数据
            ChatSendRequest request = parseChatSendRequest(envelope.getBody());
            if (request == null) {
                WSMessage errorResp = WSMessage.paramError(operationID, "消息数据格式错误");
                return HandleResult.failure("消息数据格式错误", errorResp);
            }

            if (isReadReceipt(request)) {
                ReadReceiptPayload payload = parseReadReceiptPayload(request.getContent());
                validateReadReceipt(payload);
                receiptAckRpc.apply(toReceiptAckReq(context, connection, payload));
                connection.incrementSendMsg();
                return HandleResult.success(WSMessage.sendMsgResp(
                        operationID,
                        null,
                        request.getClientMsgId(),
                        System.currentTimeMillis(),
                        null));
            }

            Message msgData = toMessage(request, connection);
            
            // 验证消息参数
            String validationError = validateMessage(msgData, connection);
            if (validationError != null) {
                WSMessage errorResp = WSMessage.paramError(operationID, validationError);
                return HandleResult.failure(validationError, errorResp);
            }
            
            // 设置发送者信息
            msgData.setSendID(context.getUserId());
            msgData.setPlatformID(context.getPlatformId() != null ? context.getPlatformId() : connection.getPlatformID());
            
            SendMessageReq req = messageSendReqMapper.map(msgData, connection, operationID);
            SendMessageResp deliveryResult = messageSendRpc.sendMessage(req);
            
            // 更新连接统计
            connection.incrementSendMsg();
            
            // 检查发送结果
            if (deliveryResult == null || !deliveryResult.isAccepted()) {
                logger.warn("Message send failed: userID={}, clientMsgID={}",
                           connection.getUserID(), msgData.getClientMsgID());

                WSMessage errorResp = WSMessage.errorResp(operationID,
                                                         1004,
                                                         "消息发送失败");
                return HandleResult.failure("消息发送失败", errorResp);
            }
            
            logger.info("Message accepted successfully: userID={}, clientMsgID={}, serverMsgID={}",
                       context.getUserId(), msgData.getClientMsgID(), deliveryResult.getServerMsgId());
            
            // 创建发送消息响应
            WSMessage sendMsgRespMsg = WSMessage.sendMsgResp(operationID, 
                                                            deliveryResult.getServerMsgId(),
                                                            msgData.getClientMsgID(),
                                                            System.currentTimeMillis(),
                                                            null);
            
            return HandleResult.success(sendMsgRespMsg);
            
        } catch (IllegalStateException e) {
            WSMessage errorResp = WSMessage.permissionError(envelope.getRequestId(), e.getMessage());
            return HandleResult.failureAndClose(e.getMessage(), errorResp);
        } catch (Exception e) {
            logger.error("Failed to handle chat message: userID={}, connectionID={}", 
                        connection.getUserID(), connection.getConnectionID(), e);
            
            WSMessage errorResp = WSMessage.internalError(envelope.getRequestId(), "消息处理失败");
            return HandleResult.failure("消息处理失败", errorResp);
        }
    }
    
    @Override
    public CommandType getSupportedCommand() {
        return CommandType.CHAT_SEND;
    }
    
    /**
     * 解析聊天请求数据
     */
    private ChatSendRequest parseChatSendRequest(Object data) {
        try {
            if (data instanceof ChatSendRequest) {
                return (ChatSendRequest) data;
            } else if (data instanceof Map) {
                return objectMapper.convertValue(data, ChatSendRequest.class);
            } else if (data instanceof String) {
                return objectMapper.readValue((String) data, ChatSendRequest.class);
            } else {
                return objectMapper.convertValue(data, ChatSendRequest.class);
            }
        } catch (Exception e) {
            logger.error("Failed to parse chat request data: {}", data, e);
            return null;
        }
    }

    private ReadReceiptPayload parseReadReceiptPayload(String content) {
        try {
            return objectMapper.readValue(content, ReadReceiptPayload.class);
        } catch (Exception e) {
            logger.error("Failed to parse read receipt payload: {}", content, e);
            return null;
        }
    }

    private ReceiptAckReq toReceiptAckReq(ConnectionContext context, UserConnection connection, ReadReceiptPayload payload) {
        ReceiptAckReq req = new ReceiptAckReq();
        req.setAckType(payload.getReceiptType());
        req.setConversationId(payload.getConversationId());
        req.setServerMsgId(payload.getServerMsgId());
        req.setSeq(payload.getSeq());
        req.setEventTime(payload.getReceiptTime());
        req.setUserId(context.getUserId() != null ? context.getUserId() : connection.getUserID());
        req.setDeviceId(context.getDeviceId() != null ? context.getDeviceId() : connection.getDeviceID());
        return req;
    }

    private boolean isReadReceipt(ChatSendRequest request) {
        if (request == null || request.getContentType() == null) {
            return false;
        }
        try {
            return ContentType.fromCode(request.getContentType()) == ContentType.READ_RECEIPT;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void validateReadReceipt(ReadReceiptPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("回执数据格式错误");
        }
        if (payload.getReceiptType() == null) {
            throw new IllegalArgumentException("回执类型不能为空");
        }
        if (payload.getConversationId() == null || payload.getConversationId().isBlank()) {
            throw new IllegalArgumentException("会话ID不能为空");
        }
        if (payload.getReceiptType() == ReceiptType.READ_CURSOR && payload.getSeq() == null) {
            throw new IllegalArgumentException("已读游标不能为空");
        }
        if ((payload.getReceiptType() == ReceiptType.RECEIVED || payload.getReceiptType() == ReceiptType.DELIVERED)
                && (payload.getServerMsgId() == null || payload.getServerMsgId().isBlank())) {
            throw new IllegalArgumentException("消息ID不能为空");
        }
    }

    private Message toMessage(ChatSendRequest request, UserConnection connection) {
        Message message = new Message();
        message.setClientMsgID(request.getClientMsgId());
        message.setRecvID(request.getRecvId());
        message.setGroupID(request.getGroupId());
        message.setContent(request.getContent());
        message.setContentType(request.getContentType());
        message.setSessionType(request.getSessionType());
        message.setSendTime(request.getSendTime());
        if (request.getOptions() != null) {
            message.setOptions(objectMapper.convertValue(request.getOptions(), new TypeReference<Map<String, Boolean>>() {}));
        }
        if (request.getExt() != null && !request.getExt().isEmpty()) {
            message.setAttachedInfo(request.getExt().get("attachedInfo"));
        }
        ConnectionContext context = connection.getContext();
        if (context != null && context.getPlatformId() != null) {
            message.setPlatformID(context.getPlatformId());
        } else {
            message.setPlatformID(connection.getPlatformID());
        }
        message.setSendID(context != null && context.getUserId() != null ? context.getUserId() : connection.getUserID());
        return message;
    }
    
    /**
     * 验证消息参数
     */
    private String validateMessage(Message message, UserConnection connection) {
        // 检查客户端消息ID
        if (message.getClientMsgID() == null || message.getClientMsgID().trim().isEmpty()) {
            return "客户端消息ID不能为空";
        }
        
        // 检查消息内容
        if (message.getContent() == null || message.getContent().trim().isEmpty()) {
            return "消息内容不能为空";
        }
        
        // 检查消息类型
        if (message.getContentType() == null || message.getContentType() <= 0) {
            return "消息类型无效";
        }
        
        // 检查会话类型
        if (message.getSessionType() == null || message.getSessionType() <= 0) {
            return "会话类型无效";
        }
        
        // 根据会话类型验证接收者信息
        switch (message.getSessionType()) {
            case 1: // 单聊
                if (message.getRecvID() == null || message.getRecvID().trim().isEmpty()) {
                    return "单聊消息接收者ID不能为空";
                }
                if (message.getRecvID().equals(connection.getUserID())) {
                    return "不能给自己发送消息";
                }
                break;
                
            case 2: // 群聊
                if (message.getGroupID() == null || message.getGroupID().trim().isEmpty()) {
                    return "群聊消息群组ID不能为空";
                }
                break;
                
            default:
                return "不支持的会话类型: " + message.getSessionType();
        }
        
        // 检查消息长度限制
        if (message.getContent().length() > 4096) {
            return "消息内容过长，最大支持4096个字符";
        }
        
        return null; // 验证通过
    }
}
