package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.postoffice.auth.AuthService;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.protocol.WSMessageType;
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
    private AuthService authService;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public HandleResult handle(UserConnection connection, WSMessage message) {
        try {
            String operationID = message.getOperationID();
            
            // 检查消息数据
            if (message.getData() == null) {
                WSMessage errorResp = WSMessage.authFailed(operationID, "认证数据不能为空");
                return HandleResult.failure("认证数据不能为空", errorResp);
            }
            
            // 解析认证数据
            Map<String, Object> authData = parseAuthData(message.getData());
            if (authData == null) {
                WSMessage errorResp = WSMessage.authFailed(operationID, "认证数据格式错误");
                return HandleResult.failure("认证数据格式错误", errorResp);
            }
            
            String token = (String) authData.get("token");
            String userID = (String) authData.get("userID");
            Integer platformID = (Integer) authData.get("platformID");
            
            // 参数验证
            if (token == null || token.trim().isEmpty()) {
                WSMessage errorResp = WSMessage.authFailed(operationID, "Token不能为空");
                return HandleResult.failure("Token不能为空", errorResp);
            }
            
            if (userID == null || userID.trim().isEmpty()) {
                WSMessage errorResp = WSMessage.authFailed(operationID, "用户ID不能为空");
                return HandleResult.failure("用户ID不能为空", errorResp);
            }
            
            if (platformID == null || platformID <= 0) {
                WSMessage errorResp = WSMessage.authFailed(operationID, "平台ID无效");
                return HandleResult.failure("平台ID无效", errorResp);
            }
            
            // 验证Token
            AuthService.AuthResult authResult = authService.validateToken(token);
            if (!authResult.isSuccess()) {
                logger.warn("Authentication failed: userID={}, platformID={}, reason={}", 
                           userID, platformID, authResult.getErrorMessage());
                
                WSMessage errorResp = WSMessage.authFailed(operationID, authResult.getErrorMessage());
                return HandleResult.failureAndClose("认证失败: " + authResult.getErrorMessage(), errorResp);
            }
            
            // 验证用户ID和平台ID是否匹配
            if (!userID.equals(authResult.getUserID()) || !platformID.equals(authResult.getPlatformID())) {
                logger.warn("Authentication mismatch: expected userID={}, platformID={}, but got userID={}, platformID={}", 
                           authResult.getUserID(), authResult.getPlatformID(), userID, platformID);
                
                WSMessage errorResp = WSMessage.authFailed(operationID, "用户信息不匹配");
                return HandleResult.failureAndClose("用户信息不匹配", errorResp);
            }
            
            // 更新连接信息
            connection.setUserID(userID);
            connection.setPlatformID(platformID);
            connection.setAuthenticated(token);

            // 将连接添加到连接管理器（处理多端登录策略）
            boolean added = connectionManager.addConnection(connection);
            if (!added) {
                logger.warn("Failed to add connection to manager: userID={}, connectionID={}",
                           userID, connection.getConnectionID());
                WSMessage errorResp = WSMessage.authFailed(operationID, "连接添加失败");
                return HandleResult.failureAndClose("连接添加失败", errorResp);
            }

            logger.info("Authentication success: userID={}, platformID={}, connectionID={}",
                       userID, platformID, connection.getConnectionID());

            // 发送认证成功响应
            WSMessage successResp = WSMessage.authSuccess(operationID, userID);

            // 发送用户上线通知给其他连接
            notifyUserOnline(connection);

            return HandleResult.success(successResp);
            
        } catch (Exception e) {
            logger.error("Failed to handle auth message: connectionID={}", 
                        connection.getConnectionID(), e);
            
            WSMessage errorResp = WSMessage.internalError(message.getOperationID(), "服务器内部错误");
            return HandleResult.failureAndClose("处理认证消息失败", errorResp);
        }
    }
    
    @Override
    public int getSupportedMessageType() {
        return WSMessageType.WS_AUTH_REQ;
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
