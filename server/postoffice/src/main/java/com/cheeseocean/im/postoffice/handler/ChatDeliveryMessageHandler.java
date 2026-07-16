package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.conversation.DeliveryStateService;
import com.cheeseocean.im.common.api.dto.conversation.DeliverySeqUpdate;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatDeliveryAckCommand;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 客户端设备批量送达 ACK 入口。 */
@Component
public class ChatDeliveryMessageHandler implements MessageHandler {
    private final ConnectionSessionGuard sessionGuard;
    @DubboReference(check = false, retries = 0)
    private DeliveryStateService deliveryStateService;

    public ChatDeliveryMessageHandler(ConnectionSessionGuard sessionGuard) { this.sessionGuard = sessionGuard; }

    @Override
    public HandleResult handle(UserConnection connection, ClientEnvelope envelope) {
        String requestId = envelope.getRequestId();
        try {
            if (!connection.isAuthenticated()) return HandleResult.failure("连接未认证", ServerEnvelope.error(requestId, 403, "连接未认证"));
            sessionGuard.ensureAuthenticated(connection);
            ProtoChatDeliveryAckCommand command = ProtoChatDeliveryAckCommand.parseFrom(envelope.getBody());
            if (command.getConversationId().isBlank() || command.getDeviceId().isBlank() || command.getOpId().isBlank()
                    || command.getMaxDeliveredSeq() <= 0 || !command.getDeviceId().equals(connection.getDeviceId())) {
                return HandleResult.failure("送达参数无效", ServerEnvelope.error(requestId, 400, "送达参数无效"));
            }
            DeliverySeqUpdate update = deliveryStateService.acknowledge(connection.getUserID(), command.getDeviceId(),
                    command.getConversationId(), command.getMaxDeliveredSeq(), command.getOpId());
            if (update == null) return HandleResult.failure("送达位点无效", ServerEnvelope.error(requestId, 400, "送达位点无效"));
            return HandleResult.success(ServerEnvelope.of(CommandType.CHAT_DELIVERY, requestId, Map.of(
                    "conversationId", update.getConversationId(), "recipientId", update.getRecipientUserId(),
                    "deviceId", update.getDeviceId(), "deliveredSeq", update.getDeliveredSeq(),
                    "updatedAt", System.currentTimeMillis())));
        } catch (Exception exception) {
            return HandleResult.failure("送达处理失败", ServerEnvelope.error(requestId, 400, "送达处理失败"));
        }
    }

    @Override public CommandType getSupportedCommand() { return CommandType.CHAT_DELIVERY; }
}
