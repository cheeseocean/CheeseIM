package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ProtoEnvelopeMapper;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 聊天消息处理器
 * 处理客户端发送的聊天消息，调用postbox服务进行消息处理
 *
 * @author xxxcrel
 */
@Component
public class ChatMessageHandler implements MessageHandler {

    private static final Logger logger = CommonLoggers.POSTOFFICE;

    @DubboReference(check = false)
    private MessageSender messageSender;

    private final ConnectionSessionGuard connectionSessionGuard;
    private final ServerProperties.MessageLimitsConfig messageLimits;

    public ChatMessageHandler(ConnectionSessionGuard connectionSessionGuard, ServerProperties serverProperties) {
        this.connectionSessionGuard = connectionSessionGuard;
        this.messageLimits = serverProperties.getMessageLimits();
    }

    @Override
    public HandleResult handle(UserConnection connection, ClientEnvelope envelope) {
        try {
            String operationID = envelope.getRequestId();

            // 检查用户是否已认证
            if (!connection.isAuthenticated()) {
                ServerEnvelope errorResp = ServerEnvelope.error(operationID, 403, "用户未认证，无法发送消息");
                return HandleResult.failure("用户未认证", errorResp);
            }

            ConnectionContext context = connection.getContext();
            if (context == null || !context.isAuthenticated()) {
                ServerEnvelope errorResp = ServerEnvelope.error(operationID, 403, "连接上下文无效");
                return HandleResult.failure("连接上下文无效", errorResp);
            }

            connectionSessionGuard.ensureAuthenticated(connection);

            // 检查消息数据
            if (envelope.getBody() == null) {
                ServerEnvelope errorResp = ServerEnvelope.error(operationID, 400, "消息数据不能为空");
                return HandleResult.failure("消息数据不能为空", errorResp);
            }
            if (envelope.getBody().length > messageLimits.getMaxEnvelopeBodyBytes()) {
                ServerEnvelope errorResp = ServerEnvelope.error(operationID, 413, "消息帧超过允许大小");
                return HandleResult.failure("消息帧超过允许大小", errorResp);
            }

            // 解析消息数据
            Message msgData = parseMessageFromBody(envelope.getBody(), connection);
            if (msgData == null) {
                ServerEnvelope errorResp = ServerEnvelope.error(operationID, 400, "消息数据格式错误");
                return HandleResult.failure("消息数据格式错误", errorResp);
            }

            // 验证消息参数
            String validationError = validateMessage(msgData, connection);
            if (validationError != null) {
                ServerEnvelope errorResp = ServerEnvelope.error(operationID, 400, validationError);
                return HandleResult.failure(validationError, errorResp);
            }

            SendMessageResp deliveryResult = messageSender.sendMessage(new SendMessageReq(msgData));

            // 更新连接统计
            connection.incrementSendMsg();

            // 检查发送结果
            if (deliveryResult == null || !deliveryResult.isAccepted()) {
                logger.warn("Message send failed: userID={}, clientMsgID={}",
                        connection.getUserID(), msgData.getClientMsgId());

                ServerEnvelope errorResp = ServerEnvelope.error(operationID, 1004, "消息发送失败");
                return HandleResult.failure("消息发送失败", errorResp);
            }

            logger.info("Message accepted successfully: userID={}, clientMsgID={}, serverMsgID={}",
                    context.getUserId(), msgData.getClientMsgId(), deliveryResult.getServerMsgId());

            // 创建发送消息响应
            ServerEnvelope sendMsgRespMsg = ServerEnvelope.chatSendAck(operationID, Map.of(
                    "serverMsgID", deliveryResult.getServerMsgId(),
                    "clientMsgID", msgData.getClientMsgId(),
                    "sendTime", System.currentTimeMillis(),
                    "acceptedAt", deliveryResult.getAcceptedAt()
            ));

            return HandleResult.success(sendMsgRespMsg);

        } catch (IllegalStateException e) {
            ServerEnvelope errorResp = ServerEnvelope.error(envelope.getRequestId(), 403, e.getMessage());
            return HandleResult.failureAndClose(e.getMessage(), errorResp);
        } catch (Exception e) {
            logger.error("Failed to handle chat message: userID={}, connectionID={}",
                    connection.getUserID(), connection.getConnectionID(), e);

            ServerEnvelope errorResp = ServerEnvelope.error(envelope.getRequestId(), 500, "消息处理失败");
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
    private Message parseMessageFromBody(byte[] data, UserConnection connection) {
        try {
            Message message = ProtoEnvelopeMapper.parseMessage(data);
            fillMessageContext(message, connection);
            return message;
        } catch (Exception e) {
            logger.error("Failed to parse chat request data: {}", data, e);
            return null;
        }
    }

    /**
     * 补齐消息在服务端上下文中可确定的字段。
     */
    private void fillMessageContext(Message message, UserConnection connection) {
        ConnectionContext context = connection.getContext();
        if (context != null && context.getPlatformCode() != null) {
            message.setPlatformType(context.getPlatformCode());
        } else {
            message.setPlatformType(connection.getPlatformType());
        }
        message.setSenderId(context != null && context.getUserId() != null ? context.getUserId() : connection.getUserID());
    }

    /**
     * 验证消息参数
     */
    private String validateMessage(Message message, UserConnection connection) {
        // 检查客户端消息ID
        if (message.getClientMsgId() == null || message.getClientMsgId().trim().isEmpty()) {
            return "客户端消息ID不能为空";
        }

        // 检查消息内容
        if (message.getContent() == null || message.getContent().length == 0) {
            return "消息内容不能为空";
        }

        // 检查消息类型
        if (message.getContentType() == null) {
            return "消息类型无效";
        }
        if (message.getContentType() == ContentType.READ_RECEIPT) {
            return "普通消息已读回执已废弃，请使用 CHAT_READ";
        }

        // 检查会话类型
        if (message.getChatType() == null) {
            return "会话类型无效";
        }

        // 根据会话类型验证接收者信息
        switch (message.getChatType()) {
            case PRIVATE: // 单聊
                if (message.getReceiverId() == null || message.getReceiverId().trim().isEmpty()) {
                    return "单聊消息接收者ID不能为空";
                }
                if (message.getReceiverId().equals(connection.getUserID())) {
                    return "不能给自己发送消息";
                }
                break;

            case GROUP: // 群聊
                if (message.getGroupId() == null || message.getGroupId().trim().isEmpty()) {
                    return "群聊消息群组ID不能为空";
                }
                break;

            default:
                return "不支持的会话类型: " + message.getChatType();
        }

        int maxContentBytes = maxContentBytes(message.getContentType());
        if (message.getContent().length > maxContentBytes) {
            return "消息内容超过允许大小: " + maxContentBytes + " bytes";
        }

        return null; // 验证通过
    }

    private int maxContentBytes(ContentType contentType) {
        return switch (contentType) {
            case TEXT -> messageLimits.getMaxTextBytes();
            case CUSTOM -> messageLimits.getMaxCustomBytes();
            case IMAGE, VOICE, VIDEO, FILE -> messageLimits.getMaxMediaMetadataBytes();
            default -> messageLimits.getMaxDefaultBytes();
        };
    }
}
