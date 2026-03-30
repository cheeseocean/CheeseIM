package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.core.util.IdGenerator;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandler;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import com.cheeseocean.im.common.core.enums.CommandType;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

/**
 * TCP服务器处理器
 * 处理TCP连接的生命周期和消息处理
 * 
 * @author xxxcrel
 */
@Component
@ChannelHandler.Sharable
public class TcpServerHandler extends SimpleChannelInboundHandler<ClientEnvelope> {
    
    private static final Logger logger = CommonLoggers.POSTOFFICE;
    
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

            if (!connectionManager.registerPendingConnection(connection)) {
                logger.warn("Failed to register pending TCP connection: connectionID={}, remoteAddress={}",
                        connectionID, ctx.channel().remoteAddress());
                ctx.close();
                return;
            }
            
            logger.info("TCP connection created: connectionID={}, clientIP={}", connectionID, clientIP);
            
            // 发送连接成功响应
            ServerEnvelope connectSuccessMsg = ServerEnvelope.connect("system", "连接成功");
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
    protected void channelRead0(ChannelHandlerContext ctx, ClientEnvelope envelope) throws Exception {
        try {
            logger.debug("Received TCP message from {}: command={}, requestId={}",
                    ctx.channel().remoteAddress(), envelope.getCommand(), envelope.getRequestId());
            
            // 获取用户连接
            UserConnection connection = connectionManager.getConnectionByChannel(ctx.channel());
            if (connection == null) {
                logger.warn("Connection not found for channel: {}", ctx.channel().remoteAddress());
                sendErrorResponse(ctx, envelope.getRequestId(), "连接不存在");
                return;
            }
            
            // 更新连接活跃时间
            connection.updateLastActiveTime();
            
            // 处理消息
            handleMessage(ctx, connection, envelope);
            
        } catch (Exception e) {
            logger.error("Failed to process TCP message from {}", ctx.channel().remoteAddress(), e);
            sendErrorResponse(ctx, envelope != null ? envelope.getRequestId() : "unknown",
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
    private void handleMessage(ChannelHandlerContext ctx, UserConnection connection, ClientEnvelope envelope) {
        CommandType commandType = null;
        try {
            commandType = envelope.getCommand();

            // 获取消息处理器
            MessageHandler handler = messageHandlerFactory.getHandler(commandType);
            if (handler == null) {
                logger.warn("Unsupported command type: {} from {}",
                        commandType, ctx.channel().remoteAddress());
                
                sendErrorResponse(ctx, envelope.getRequestId(),
                                "不支持的消息类型: " + commandType);
                return;
            }
            
            // 处理消息
            MessageHandler.HandleResult result = handler.handle(connection, envelope);
            
            // 发送响应消息
            if (result.getResponseEnvelope() != null) {
                sendMessage(ctx, result.getResponseEnvelope());
            }
            
            // 如果需要关闭连接
            if (result.isShouldClose()) {
                ctx.close();
            }
            
        } catch (Exception e) {
            logger.error("Failed to handle TCP message: commandType={}, operationID={}",
                    commandType,
                    envelope != null ? envelope.getRequestId() : null,
                    e);
            
            sendErrorResponse(ctx, envelope != null ? envelope.getRequestId() : null, "消息处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送TCP消息
     */
    private void sendMessage(ChannelHandlerContext ctx, ServerEnvelope message) {
        try {
            ctx.writeAndFlush(message);
            logger.debug("Sent TCP message to {}: command={}, requestId={}",
                    ctx.channel().remoteAddress(), message.getCommand(), message.getRequestId());
            
        } catch (Exception e) {
            logger.error("Failed to send TCP message to {}", ctx.channel().remoteAddress(), e);
        }
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, String operationID, String errorMessage) {
        try {
            ServerEnvelope errorMsg = ServerEnvelope.error(operationID, 500, errorMessage);
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
