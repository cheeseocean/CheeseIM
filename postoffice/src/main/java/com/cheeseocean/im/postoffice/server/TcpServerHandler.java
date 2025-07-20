package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.utils.IdGenerator;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandler;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import com.cheeseocean.im.postoffice.protocol.TcpMessage;
import com.cheeseocean.im.postoffice.protocol.TcpMessageType;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

/**
 * TCP服务器处理器
 * 处理TCP连接的生命周期和消息处理
 * 
 * @author CheeseIM
 */
@Component
public class TcpServerHandler extends SimpleChannelInboundHandler<TcpMessage> {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpServerHandler.class);
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    private MessageHandlerFactory messageHandlerFactory;
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("TCP connection established: {}", ctx.channel().remoteAddress());
        
        try {
            // 生成连接ID
            String connectionID = IdGenerator.generateUUID();
            
            // 获取客户端IP
            String clientIP = getClientIP(ctx);
            
            // 创建用户连接对象
            UserConnection connection = new UserConnection();
            connection.setConnectionID(connectionID);
            connection.setChannel(ctx.channel());
            connection.setClientIP(clientIP);
            connection.setProtocol("TCP");
            
            // 将连接与Channel关联，但暂时不添加到用户连接映射
            // 等认证成功后再调用connectionManager.addConnection
            
            logger.info("TCP connection created: connectionID={}, clientIP={}", connectionID, clientIP);
            
            // 发送连接成功响应
            TcpMessage connectSuccessMsg = TcpMessage.connectSuccess("system");
            sendMessage(ctx, connectSuccessMsg);
            
        } catch (Exception e) {
            logger.error("Failed to handle TCP connection active", e);
            ctx.close();
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("TCP connection closed: {}", ctx.channel().remoteAddress());
        
        try {
            // 从连接管理器中移除连接
            UserConnection connection = connectionManager.getConnectionByChannel(ctx.channel());
            if (connection != null) {
                connectionManager.removeConnection(connection.getConnectionID());
                logger.info("TCP connection removed: connectionID={}, userID={}", 
                           connection.getConnectionID(), connection.getUserID());
            }
            
        } catch (Exception e) {
            logger.error("Failed to handle TCP connection inactive", e);
        }
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            handleIdleStateEvent(ctx, (IdleStateEvent) evt);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TcpMessage message) throws Exception {
        try {
            logger.debug("Received TCP message from {}: msgType={}, operationID={}", 
                        ctx.channel().remoteAddress(), message.getMsgType(), message.getOperationID());
            
            // 获取用户连接
            UserConnection connection = connectionManager.getConnectionByChannel(ctx.channel());
            if (connection == null) {
                logger.warn("Connection not found for channel: {}", ctx.channel().remoteAddress());
                sendErrorResponse(ctx, message.getOperationID(), "连接不存在");
                return;
            }
            
            // 更新连接活跃时间
            connection.updateLastActiveTime();
            
            // 处理消息
            handleMessage(ctx, connection, message);
            
        } catch (Exception e) {
            logger.error("Failed to process TCP message from {}", ctx.channel().remoteAddress(), e);
            sendErrorResponse(ctx, message != null ? message.getOperationID() : "unknown", 
                            "消息处理异常: " + e.getMessage());
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in TCP connection: {}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
    
    /**
     * 处理连接空闲事件
     */
    private void handleIdleStateEvent(ChannelHandlerContext ctx, IdleStateEvent evt) {
        if (evt.state() == IdleState.ALL_IDLE) {
            logger.warn("TCP connection idle timeout: {}", ctx.channel().remoteAddress());
            
            // 发送超时通知
            sendErrorResponse(ctx, "system", "连接超时");
            
            // 关闭连接
            ctx.close();
        }
    }
    
    /**
     * 处理TCP消息
     */
    private void handleMessage(ChannelHandlerContext ctx, UserConnection connection, TcpMessage message) {
        try {
            byte messageType = message.getMsgType();
            
            // 将TCP消息转换为WebSocket消息以复用现有的处理器
            WSMessage wsMessage = message.toWSMessage();
            
            // 获取消息处理器
            MessageHandler handler = messageHandlerFactory.getHandler(wsMessage.getMsgType());
            if (handler == null) {
                logger.warn("Unsupported message type: {} from {}", 
                           messageType, ctx.channel().remoteAddress());
                
                sendErrorResponse(ctx, message.getOperationID(), 
                                "不支持的消息类型: " + messageType);
                return;
            }
            
            // 处理消息
            MessageHandler.HandleResult result = handler.handle(connection, wsMessage);
            
            // 发送响应消息
            if (result.getResponseMessage() != null) {
                TcpMessage tcpResponse = TcpMessage.fromWSMessage(result.getResponseMessage());
                sendMessage(ctx, tcpResponse);
            }
            
            // 如果需要关闭连接
            if (result.isShouldClose()) {
                ctx.close();
            }
            
        } catch (Exception e) {
            logger.error("Failed to handle TCP message: msgType={}, operationID={}", 
                        message.getMsgType(), message.getOperationID(), e);
            
            sendErrorResponse(ctx, message.getOperationID(), "消息处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送TCP消息
     */
    private void sendMessage(ChannelHandlerContext ctx, TcpMessage message) {
        try {
            ctx.writeAndFlush(message);
            logger.debug("Sent TCP message to {}: msgType={}, operationID={}", 
                        ctx.channel().remoteAddress(), message.getMsgType(), message.getOperationID());
            
        } catch (Exception e) {
            logger.error("Failed to send TCP message to {}", ctx.channel().remoteAddress(), e);
        }
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, String operationID, String errorMessage) {
        try {
            TcpMessage errorMsg = TcpMessage.errorResp(operationID, 500, errorMessage);
            sendMessage(ctx, errorMsg);
            
        } catch (Exception e) {
            logger.error("Failed to send error response to {}", ctx.channel().remoteAddress(), e);
        }
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIP(ChannelHandlerContext ctx) {
        try {
            InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            return socketAddress.getAddress().getHostAddress();
        } catch (Exception e) {
            logger.warn("Failed to get client IP", e);
            return "unknown";
        }
    }
}
