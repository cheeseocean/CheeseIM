package com.cheeseocean.im.postoffice.handler;

import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.protocol.WSMessageType;
import com.cheeseocean.im.postoffice.service.OnlineRouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 心跳消息处理器
 * 处理客户端心跳请求，维持连接活跃状态
 * 
 * @author CheeseIM
 */
@Component
public class HeartbeatMessageHandler implements MessageHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatMessageHandler.class);

    @Autowired(required = false)
    private OnlineRouteService onlineRouteService;
    
    @Override
    public HandleResult handle(UserConnection connection, WSMessage message) {
        try {
            // 更新连接的最后活跃时间
            connection.incrementHeartbeat();
            if (onlineRouteService != null && connection.getUserID() != null && connection.getPlatformID() != null) {
                onlineRouteService.refresh(connection.getUserID(),
                        connection.getPlatformName().toLowerCase() + "-" + connection.getPlatformID(),
                        connection.getLastActiveTime());
            }
            
            String operationID = message.getOperationID();
            
            // 创建心跳响应
            WSMessage heartbeatResp = WSMessage.heartbeatResp(operationID);
            
            logger.debug("Heartbeat processed: userID={}, connectionID={}, count={}", 
                        connection.getUserID(), connection.getConnectionID(), 
                        connection.getHeartbeatCount());
            
            return HandleResult.success(heartbeatResp);
            
        } catch (Exception e) {
            logger.error("Failed to handle heartbeat message: connectionID={}", 
                        connection.getConnectionID(), e);
            
            WSMessage errorResp = WSMessage.internalError(message.getOperationID(), "心跳处理失败");
            return HandleResult.failure("心跳处理失败", errorResp);
        }
    }
    
    @Override
    public int getSupportedMessageType() {
        return WSMessageType.WS_HEARTBEAT_REQ;
    }
}
