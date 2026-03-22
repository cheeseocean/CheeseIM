package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.event.ReceiptEvent;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.protocol.WSMessageType;
import com.cheeseocean.im.postoffice.service.GatewayReceiptPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReceiptMessageHandler implements MessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(ReceiptMessageHandler.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GatewayReceiptPublisher gatewayReceiptPublisher;

    @Autowired
    private ConnectionSessionGuard connectionSessionGuard;

    @Override
    public HandleResult handle(UserConnection connection, WSMessage message) {
        try {
            if (!connection.isAuthenticated()) {
                WSMessage errorResp = WSMessage.permissionError(message.getOperationID(), "用户未认证，无法上报回执");
                return HandleResult.failure("用户未认证", errorResp);
            }

            ConnectionContext context = connection.getContext();
            if (context == null || !context.isAuthenticated()) {
                WSMessage errorResp = WSMessage.permissionError(message.getOperationID(), "连接上下文无效");
                return HandleResult.failure("连接上下文无效", errorResp);
            }

            connectionSessionGuard.ensureValid(connection);

            if (message.getData() == null) {
                WSMessage errorResp = WSMessage.paramError(message.getOperationID(), "回执数据不能为空");
                return HandleResult.failure("回执数据不能为空", errorResp);
            }

            ReceiptPayload payload = parsePayload(message.getData());
            if (payload == null) {
                WSMessage errorResp = WSMessage.paramError(message.getOperationID(), "回执数据格式错误");
                return HandleResult.failure("回执数据格式错误", errorResp);
            }

            ReceiptEvent event = toReceiptEvent(connection, context, payload);
            gatewayReceiptPublisher.publish(event);
            connection.incrementRecvMsg();

            return HandleResult.success(new WSMessage(
                    WSMessageType.WS_MSG_READ_NOTIFY,
                    message.getOperationID(),
                    Map.of("status", "ACCEPTED", "receiptType", event.getReceiptType())
            ));
        } catch (IllegalArgumentException e) {
            WSMessage errorResp = WSMessage.paramError(message.getOperationID(), e.getMessage());
            return HandleResult.failure(e.getMessage(), errorResp);
        } catch (IllegalStateException e) {
            WSMessage errorResp = WSMessage.permissionError(message.getOperationID(), e.getMessage());
            return HandleResult.failureAndClose(e.getMessage(), errorResp);
        } catch (Exception e) {
            logger.error("Failed to handle receipt message: userID={}, connectionID={}",
                    connection.getUserID(), connection.getConnectionID(), e);
            WSMessage errorResp = WSMessage.internalError(message.getOperationID(), "回执处理失败");
            return HandleResult.failure("回执处理失败", errorResp);
        }
    }

    @Override
    public int getSupportedMessageType() {
        return WSMessageType.WS_MSG_READ_NOTIFY;
    }

    private ReceiptPayload parsePayload(Object data) {
        try {
            if (data instanceof ReceiptPayload payload) {
                return payload;
            }
            if (data instanceof String text) {
                return objectMapper.readValue(text, ReceiptPayload.class);
            }
            return objectMapper.convertValue(data, ReceiptPayload.class);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to parse receipt payload: {}", data, e);
            return null;
        } catch (Exception e) {
            logger.error("Failed to parse receipt payload: {}", data, e);
            return null;
        }
    }

    private ReceiptEvent toReceiptEvent(UserConnection connection, ConnectionContext context, ReceiptPayload payload) {
        String receiptType = payload.getReceiptType();
        if (receiptType == null || receiptType.isBlank()) {
            throw new IllegalArgumentException("回执类型不能为空");
        }
        if (payload.getConversationId() == null || payload.getConversationId().isBlank()) {
            throw new IllegalArgumentException("会话ID不能为空");
        }

        String userId = context.getUserId() != null ? context.getUserId() : connection.getUserID();
        String deviceId = context.getDeviceId() != null ? context.getDeviceId() : connection.getDeviceID();

        if ("DELIVERED".equals(receiptType) || "RECEIVED".equals(receiptType)) {
            if (payload.getServerMsgId() == null || payload.getServerMsgId().isBlank()) {
                throw new IllegalArgumentException("消息ID不能为空");
            }
            ReceiptEvent event = ReceiptEvent.delivered(
                    userId, payload.getConversationId(), payload.getServerMsgId(), payload.getSeq(), deviceId);
            if (payload.getReceiptTime() != null) {
                event.setReceiptTime(payload.getReceiptTime());
            }
            return event;
        }
        if ("READ_CURSOR".equals(receiptType) || "READ".equals(receiptType)) {
            if (payload.getSeq() == null) {
                throw new IllegalArgumentException("已读游标不能为空");
            }
            ReceiptEvent event = ReceiptEvent.readCursor(userId, payload.getConversationId(), payload.getSeq(), deviceId);
            if (payload.getReceiptTime() != null) {
                event.setReceiptTime(payload.getReceiptTime());
            }
            return event;
        }
        throw new IllegalArgumentException("不支持的回执类型: " + receiptType);
    }

    static class ReceiptPayload {
        private String receiptType;
        private String conversationId;
        private String serverMsgId;
        private Long seq;
        private Long receiptTime;

        public String getReceiptType() {
            return receiptType;
        }

        public void setReceiptType(String receiptType) {
            this.receiptType = receiptType;
        }

        public String getConversationId() {
            return conversationId;
        }

        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }

        public String getServerMsgId() {
            return serverMsgId;
        }

        public void setServerMsgId(String serverMsgId) {
            this.serverMsgId = serverMsgId;
        }

        public Long getSeq() {
            return seq;
        }

        public void setSeq(Long seq) {
            this.seq = seq;
        }

        public Long getReceiptTime() {
            return receiptTime;
        }

        public void setReceiptTime(Long receiptTime) {
            this.receiptTime = receiptTime;
        }
    }
}
