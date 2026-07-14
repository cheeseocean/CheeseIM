package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.conversation.ReadStateService;
import com.cheeseocean.im.common.api.dto.conversation.ReadSeqUpdate;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatReadCommand;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 已读位点长连接入口。网关只负责认证与协议解析，单调推进等领域规则由共享服务保证。
 */
@Component
public class ChatReadMessageHandler implements MessageHandler {

    private final ConnectionSessionGuard connectionSessionGuard;

    @DubboReference(check = false, retries = 0)
    private ReadStateService readStateService;

    public ChatReadMessageHandler(ConnectionSessionGuard connectionSessionGuard) {
        this.connectionSessionGuard = connectionSessionGuard;
    }

    @Override
    public HandleResult handle(UserConnection connection, ClientEnvelope envelope) {
        String requestId = envelope.getRequestId();
        try {
            if (!connection.isAuthenticated()) {
                return HandleResult.failure("连接未认证", ServerEnvelope.error(requestId, 403, "连接未认证"));
            }
            connectionSessionGuard.ensureAuthenticated(connection);
            ProtoChatReadCommand command = ProtoChatReadCommand.parseFrom(envelope.getBody());
            if (command.getConversationId().isBlank() || command.getReadSeq() < 0) {
                return HandleResult.failure("已读参数无效", ServerEnvelope.error(requestId, 400, "已读参数无效"));
            }
            ReadSeqUpdate update = readStateService.acknowledge(
                    connection.getUserID(), command.getConversationId(), command.getReadSeq());
            if (update == null) {
                return HandleResult.failure("已读位点无效", ServerEnvelope.error(requestId, 400, "已读位点无效"));
            }
            return HandleResult.success(ServerEnvelope.of(CommandType.CHAT_READ, requestId, Map.of(
                    "conversationId", update.getConversationId(),
                    "readerId", update.getReaderUserId(),
                    "readSeq", update.getReadSeq(),
                    "updatedAt", System.currentTimeMillis())));
        } catch (IllegalStateException exception) {
            return HandleResult.failureAndClose(exception.getMessage(), ServerEnvelope.error(requestId, 403, exception.getMessage()));
        } catch (Exception exception) {
            return HandleResult.failure("已读处理失败", ServerEnvelope.error(requestId, 400, "已读处理失败"));
        }
    }

    @Override
    public CommandType getSupportedCommand() {
        return CommandType.CHAT_READ;
    }
}
