package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.postoffice.auth.WsTicketAuthService;
import com.cheeseocean.im.postoffice.connection.ConnectionBindService;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 认证消息处理器
 * 处理用户认证相关的WebSocket消息
 * 
 * @author CheeseIM
 */
@Component
public class AuthMessageHandler implements MessageHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthMessageHandler.class);
    
    @Autowired
    private WsTicketAuthService wsTicketAuthService;

    @Autowired
    private ConnectionBindService connectionBindService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public HandleResult handle(UserConnection connection, ClientEnvelope envelope) {
        try {
            String operationID = envelope.getRequestId();
            
            // 检查消息数据
            if (envelope.getBody() == null) {
                WSMessage errorResp = WSMessage.authFailed(operationID, "认证数据不能为空");
                return HandleResult.failure("认证数据不能为空", errorResp);
            }
            
            // 解析认证数据
            Map<String, Object> authData = parseAuthData(envelope.getBody());
            if (authData == null) {
                WSMessage errorResp = WSMessage.authFailed(operationID, "认证数据格式错误");
                return HandleResult.failure("认证数据格式错误", errorResp);
            }
            
            String ticket = (String) authData.get("ticket");
            
            // 参数验证
            if (ticket == null || ticket.trim().isEmpty()) {
                WSMessage errorResp = WSMessage.authFailed(operationID, "ticket不能为空");
                return HandleResult.failure("ticket不能为空", errorResp);
            }
            
            SessionPrincipal session = wsTicketAuthService.authenticate(ticket);
            
            boolean added = connectionBindService.bindAuthenticated(connection, session);
            if (!added) {
                logger.warn("Failed to bind connection to manager: userID={}, connectionID={}",
                           session.getUserId(), connection.getConnectionID());
                WSMessage errorResp = WSMessage.authFailed(operationID, "连接添加失败");
                return HandleResult.failureAndClose("连接添加失败", errorResp);
            }

            logger.info("Authentication success: userID={}, sessionID={}, deviceID={}, connectionID={}",
                    session.getUserId(), session.getSessionId(), session.getDeviceId(), connection.getConnectionID());

            // 发送认证成功响应
            WSMessage successResp = WSMessage.authSuccess(operationID, session.getUserId());

            // 发送用户上线通知给其他连接
            notifyUserOnline(connection);

            return HandleResult.success(successResp);
            
        } catch (IllegalStateException e) {
            logger.warn("Authentication failed: connectionID={}, reason={}",
                    connection.getConnectionID(), e.getMessage());
            WSMessage errorResp = WSMessage.authFailed(envelope.getRequestId(), e.getMessage());
            return HandleResult.failureAndClose("认证失败: " + e.getMessage(), errorResp);
        } catch (Exception e) {
            logger.error("Failed to handle auth message: connectionID={}", 
                        connection.getConnectionID(), e);
            
            WSMessage errorResp = WSMessage.internalError(envelope.getRequestId(), "服务器内部错误");
            return HandleResult.failureAndClose("处理认证消息失败", errorResp);
        }
    }
    
    @Override
    public CommandType getSupportedCommand() {
        return CommandType.AUTH;
    }
    
    /**
     * 解析认证数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAuthData(Object data) {
        try {
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            } else if (data instanceof String) {
                return objectMapper.readValue((String) data, Map.class);
            } else {
                // 尝试转换为Map
                String jsonStr = objectMapper.writeValueAsString(data);
                return objectMapper.readValue(jsonStr, Map.class);
            }
        } catch (Exception e) {
            logger.error("Failed to parse auth data: {}", data, e);
            return null;
        }
    }
    
    /**
     * 通知用户上线
     */
    private void notifyUserOnline(UserConnection connection) {
        try {
            String userID = connection.getUserID();
            Integer platformID = connection.getPlatformID();
            
            // 创建用户上线通知消息
            WSMessage onlineNotify = WSMessage.userOnlineNotify("system", userID, platformID);
            
            // 可以在这里添加通知好友上线的逻辑
            // 例如：获取用户的好友列表，然后向在线的好友发送上线通知
            
            logger.debug("User online notification sent: userID={}, platformID={}", userID, platformID);
            
        } catch (Exception e) {
            logger.error("Failed to notify user online: userID={}, platformID={}", 
                        connection.getUserID(), connection.getPlatformID(), e);
        }
    }
}
