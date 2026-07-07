package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.postoffice.auth.ConnectionSessionGuard;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 心跳消息处理器
 * 处理客户端心跳请求，维持连接活跃状态
 * 
 * @author xxxcrel
 */
@Component
public class HeartbeatMessageHandler implements MessageHandler {
    
    private static final Logger logger = CommonLoggers.POSTOFFICE;

    @Autowired(required = false)
    private OnlineRouteService onlineRouteService;

    @Autowired
    private ConnectionSessionGuard connectionSessionGuard;
    
    @Override
    public HandleResult handle(UserConnection connection, ClientEnvelope envelope) {
        try {
            if (!connection.isAuthenticated()) {
                ServerEnvelope errorResp = ServerEnvelope.error(envelope.getRequestId(), 403, "连接未认证");
                return HandleResult.failure("连接未认证", errorResp);
            }

            connectionSessionGuard.ensureSessionActive(connection);

            // 更新连接的最后活跃时间
            connection.incrementHeartbeat();
            String deviceId = ConnectionManager.routeDeviceId(connection);
            if (onlineRouteService != null && connection.getUserID() != null && deviceId != null) {
                onlineRouteService.refresh(connection.getUserID(),
                        deviceId,
                        connection.getLastActiveTime());
            }
            
            String operationID = envelope.getRequestId();
            
            // 创建心跳响应
            ServerEnvelope heartbeatResp = ServerEnvelope.heartbeat(operationID, "pong");
            
            logger.debug("Heartbeat processed: userID={}, connectionID={}, count={}", 
                        connection.getUserID(), connection.getConnectionID(), 
                        connection.getHeartbeatCount());
            
            return HandleResult.success(heartbeatResp);
            
        } catch (IllegalStateException e) {
            ServerEnvelope errorResp = ServerEnvelope.error(envelope.getRequestId(), 403, e.getMessage());
            return HandleResult.failureAndClose(e.getMessage(), errorResp);
        } catch (Exception e) {
            logger.error("Failed to handle heartbeat message: connectionID={}", 
                        connection.getConnectionID(), e);
            
            ServerEnvelope errorResp = ServerEnvelope.error(envelope.getRequestId(), 500, "心跳处理失败");
            return HandleResult.failure("心跳处理失败", errorResp);
        }
    }
    
    @Override
    public CommandType getSupportedCommand() {
        return CommandType.HEARTBEAT;
    }
}
