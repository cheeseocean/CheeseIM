package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.dto.message.MessageMutationResult;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.message.MessageMutationService;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatRevokeCommand;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 撤回长连接入口。撤回权限、时间窗和幂等性由消息 mutation 服务集中处理。
 */
@Component
public class ChatRevokeMessageHandler implements MessageHandler {

    private final ConnectionSessionGuard connectionSessionGuard;

    @DubboReference(check = false, retries = 0)
    private MessageMutationService messageMutationService;

    public ChatRevokeMessageHandler(ConnectionSessionGuard connectionSessionGuard) {
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
            ProtoChatRevokeCommand command = ProtoChatRevokeCommand.parseFrom(envelope.getBody());
            if (command.getConversationId().isBlank() || command.getServerMsgId().isBlank()) {
                return HandleResult.failure("撤回参数无效", ServerEnvelope.error(requestId, 400, "撤回参数无效"));
            }
            MessageMutationResult result = messageMutationService.revoke(
                    connection.getUserID(), command.getConversationId(), command.getServerMsgId(), command.getReason());
            if (result == null || !result.isSuccess()) {
                String message = result == null ? "撤回失败" : result.getErrorMessage();
                return HandleResult.failure(message, ServerEnvelope.error(requestId, 400, message));
            }
            return HandleResult.success(ServerEnvelope.of(CommandType.CHAT_REVOKE, requestId, Map.of(
                    "conversationId", result.getConversationId(),
                    "serverMsgId", result.getServerMsgId(),
                    "operatorUserId", result.getOperatorUserId(),
                    "operatorName", result.getOperatorName() == null ? "" : result.getOperatorName(),
                    "targetSenderId", result.getTargetSenderId(),
                    "targetSenderName", result.getTargetSenderName() == null ? "" : result.getTargetSenderName(),
                    "revokedAt", result.getRevokedAt(),
                    "mutationVersion", result.getMutationVersion())));
        } catch (IllegalStateException exception) {
            return HandleResult.failureAndClose(exception.getMessage(), ServerEnvelope.error(requestId, 403, exception.getMessage()));
        } catch (Exception exception) {
            return HandleResult.failure("撤回处理失败", ServerEnvelope.error(requestId, 400, "撤回处理失败"));
        }
    }

    @Override
    public CommandType getSupportedCommand() {
        return CommandType.CHAT_REVOKE;
    }
}
