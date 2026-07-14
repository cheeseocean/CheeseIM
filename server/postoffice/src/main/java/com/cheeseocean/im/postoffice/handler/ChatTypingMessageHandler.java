package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.conversation.TypingStateService;
import com.cheeseocean.im.common.api.dto.conversation.TypingSignal;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.api.enums.TypingActionEnum;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.protocol.proto.ProtoChatTypingCommand;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 输入中长连接入口。
 *
 * <p>只解析 typed 控制命令并调用共享服务，绝不转换为普通 {@code ChatMessage}。
 */
@Component
public class ChatTypingMessageHandler implements MessageHandler {

    private final ConnectionSessionGuard connectionSessionGuard;

    @DubboReference(check = false, retries = 0)
    private TypingStateService typingStateService;

    public ChatTypingMessageHandler(ConnectionSessionGuard connectionSessionGuard) {
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
            ProtoChatTypingCommand command = ProtoChatTypingCommand.parseFrom(envelope.getBody());
            TypingActionEnum action = TypingActionEnum.fromCode(command.getAction());
            if (command.getConversationId().isBlank()) {
                return HandleResult.failure("输入中参数无效", ServerEnvelope.error(requestId, 400, "输入中参数无效"));
            }
            TypingSignal signal = typingStateService.publish(
                    connection.getUserID(), command.getConversationId(), action, command.getTtlSeconds());
            if (signal == null) {
                return HandleResult.failure("无会话访问权限", ServerEnvelope.error(requestId, 403, "无会话访问权限"));
            }
            return HandleResult.success(ServerEnvelope.of(CommandType.CHAT_TYPING, requestId, Map.of(
                    "conversationId", signal.getConversationId(),
                    "senderId", signal.getSenderId(),
                    "action", signal.getAction().getCode(),
                    "expiresAt", signal.getExpiresAt())));
        } catch (IllegalArgumentException exception) {
            return HandleResult.failure("输入中参数无效", ServerEnvelope.error(requestId, 400, "输入中参数无效"));
        } catch (IllegalStateException exception) {
            return HandleResult.failureAndClose(exception.getMessage(), ServerEnvelope.error(requestId, 403, exception.getMessage()));
        } catch (Exception exception) {
            return HandleResult.failure("输入中处理失败", ServerEnvelope.error(requestId, 400, "输入中处理失败"));
        }
    }

    @Override
    public CommandType getSupportedCommand() {
        return CommandType.CHAT_TYPING;
    }
}
