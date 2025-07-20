package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.entity.Message;
import com.cheeseocean.im.common.service.MessageService;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.protocol.WSMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    
    @DubboReference
    private MessageService messageService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public HandleResult handle(UserConnection connection, WSMessage message) {
        try {
            String operationID = message.getOperationID();
            
            // 检查用户是否已认证
            if (!connection.isAuthenticated()) {
                WSMessage errorResp = WSMessage.permissionError(operationID, "用户未认证，无法发送消息");
                return HandleResult.failure("用户未认证", errorResp);
            }
            
            // 检查消息数据
            if (message.getData() == null) {
                WSMessage errorResp = WSMessage.paramError(operationID, "消息数据不能为空");
                return HandleResult.failure("消息数据不能为空", errorResp);
            }
            
            // 解析消息数据
            Message msgData = parseMessageData(message.getData());
            if (msgData == null) {
                WSMessage errorResp = WSMessage.paramError(operationID, "消息数据格式错误");
                return HandleResult.failure("消息数据格式错误", errorResp);
            }
            
            // 验证消息参数
            String validationError = validateMessage(msgData, connection);
            if (validationError != null) {
                WSMessage errorResp = WSMessage.paramError(operationID, validationError);
                return HandleResult.failure(validationError, errorResp);
            }
            
            // 设置发送者信息
            msgData.setSendID(connection.getUserID());
            msgData.setPlatformID(connection.getPlatformID());
            
            // 构建发送消息请求
            SendMsgReq sendMsgReq = new SendMsgReq(msgData, operationID);
            
            // 调用postbox服务发送消息
            SendMsgResp sendMsgResp = messageService.sendMsg(sendMsgReq);
            
            // 更新连接统计
            connection.incrementSendMsg();
            
            // 检查发送结果
            if (sendMsgResp.getErrCode() != null && sendMsgResp.getErrCode() != 0) {
                logger.warn("Message send failed: userID={}, clientMsgID={}, errCode={}, errMsg={}", 
                           connection.getUserID(), msgData.getClientMsgID(), 
                           sendMsgResp.getErrCode(), sendMsgResp.getErrMsg());
                
                WSMessage errorResp = WSMessage.errorResp(operationID, 
                                                         sendMsgResp.getErrCode(), 
                                                         sendMsgResp.getErrMsg());
                return HandleResult.failure("消息发送失败", errorResp);
            }
            
            logger.info("Message sent successfully: userID={}, clientMsgID={}, serverMsgID={}", 
                       connection.getUserID(), sendMsgResp.getClientMsgID(), sendMsgResp.getServerMsgID());
            
            // 创建发送消息响应
            WSMessage sendMsgRespMsg = WSMessage.sendMsgResp(operationID, 
                                                            sendMsgResp.getServerMsgID(),
                                                            sendMsgResp.getClientMsgID(),
                                                            sendMsgResp.getSendTime());
            
            return HandleResult.success(sendMsgRespMsg);
            
        } catch (Exception e) {
            logger.error("Failed to handle chat message: userID={}, connectionID={}", 
                        connection.getUserID(), connection.getConnectionID(), e);
            
            WSMessage errorResp = WSMessage.internalError(message.getOperationID(), "消息处理失败");
            return HandleResult.failure("消息处理失败", errorResp);
        }
    }
    
    @Override
    public int getSupportedMessageType() {
        return WSMessageType.WS_SEND_MSG_REQ;
    }
    
    /**
     * 解析消息数据
     */
    private Message parseMessageData(Object data) {
        try {
            if (data instanceof Message) {
                return (Message) data;
            } else if (data instanceof Map) {
                String jsonStr = objectMapper.writeValueAsString(data);
                return objectMapper.readValue(jsonStr, Message.class);
            } else if (data instanceof String) {
                return objectMapper.readValue((String) data, Message.class);
            } else {
                // 尝试转换为Message对象
                String jsonStr = objectMapper.writeValueAsString(data);
                return objectMapper.readValue(jsonStr, Message.class);
            }
        } catch (Exception e) {
            logger.error("Failed to parse message data: {}", data, e);
            return null;
        }
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
