package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.utils.IdGenerator;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandler;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.protocol.WSMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

/**
 * WebSocket服务器处理器
 * 处理WebSocket连接的生命周期和消息处理
 * 
 * @author CheeseIM
 */
@Component
public class WebSocketServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketServerHandler.class);
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    private MessageHandlerFactory messageHandlerFactory;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // WebSocket握手完成
            handleHandshakeComplete(ctx, (WebSocketServerProtocolHandler.HandshakeComplete) evt);
        } else if (evt instanceof IdleStateEvent) {
            // 连接空闲事件
            handleIdleStateEvent(ctx, (IdleStateEvent) evt);
        }
        super.userEventTriggered(ctx, evt);
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        try {
            String messageText = frame.text();
            logger.debug("Received message from {}: {}", ctx.channel().remoteAddress(), messageText);
            
            // 解析WebSocket消息
            WSMessage message = parseMessage(messageText);
            if (message == null) {
                sendErrorResponse(ctx, "system", "消息格式错误");
                return;
            }
            
            // 获取用户连接
            UserConnection connection = connectionManager.getConnectionByChannel(ctx.channel());
            if (connection == null) {
                logger.warn("Connection not found for channel: {}", ctx.channel().remoteAddress());
                sendErrorResponse(ctx, message.getOperationID(), "连接不存在");
                return;
            }
            
            // 更新连接活跃时间
            connection.updateLastActiveTime();
            connection.incrementRecvMsg();
            
            // 处理消息
            handleMessage(ctx, connection, message);
            
        } catch (Exception e) {
            logger.error("Failed to process message from {}", ctx.channel().remoteAddress(), e);
            sendErrorResponse(ctx, "system", "消息处理失败");
        }
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Channel active: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Channel inactive: {}", ctx.channel().remoteAddress());
        
        // 移除连接
        connectionManager.removeConnectionByChannel(ctx.channel());
        
        super.channelInactive(ctx);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception caught in channel {}", ctx.channel().remoteAddress(), cause);
        
        // 发送错误响应
        sendErrorResponse(ctx, "system", "服务器内部错误");
        
        // 关闭连接
        ctx.close();
    }
    
    /**
     * 处理WebSocket握手完成事件
     */
    private void handleHandshakeComplete(ChannelHandlerContext ctx,
                                       WebSocketServerProtocolHandler.HandshakeComplete evt) {
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
            connection.setUserAgent(evt.requestHeaders().get("User-Agent"));

            // 将连接与Channel关联，但暂时不添加到用户连接映射
            // 等认证成功后再调用connectionManager.addConnection

            logger.info("WebSocket handshake complete: connectionID={}, clientIP={}, userAgent={}",
                       connectionID, clientIP, connection.getUserAgent());

            // 发送连接成功响应
            WSMessage connectSuccessMsg = WSMessage.connectSuccess("system");
            sendMessage(ctx, connectSuccessMsg);

        } catch (Exception e) {
            logger.error("Failed to handle handshake complete", e);
            ctx.close();
        }
    }
    
    /**
     * 处理连接空闲事件
     */
    private void handleIdleStateEvent(ChannelHandlerContext ctx, IdleStateEvent evt) {
        if (evt.state() == IdleState.ALL_IDLE) {
            logger.warn("Connection idle timeout: {}", ctx.channel().remoteAddress());
            
            // 发送超时通知
            sendErrorResponse(ctx, "system", "连接超时");
            
            // 关闭连接
            ctx.close();
        }
    }
    
    /**
     * 处理WebSocket消息
     */
    private void handleMessage(ChannelHandlerContext ctx, UserConnection connection, WSMessage message) {
        try {
            int messageType = message.getMsgType();
            
            // 获取消息处理器
            MessageHandler handler = messageHandlerFactory.getHandler(messageType);
            if (handler == null) {
                logger.warn("Unsupported message type: {} from {}", 
                           messageType, ctx.channel().remoteAddress());
                
                sendErrorResponse(ctx, message.getOperationID(), 
                                "不支持的消息类型: " + messageType);
                return;
            }
            
            // 处理消息
            MessageHandler.HandleResult result = handler.handle(connection, message);
            
            // 发送响应消息
            if (result.getResponseMessage() != null) {
                sendMessage(ctx, result.getResponseMessage());
            }
            
            // 如果处理失败，记录日志
            if (!result.isSuccess()) {
                logger.warn("Message handling failed: messageType={}, error={}, from={}", 
                           messageType, result.getErrorMessage(), ctx.channel().remoteAddress());
            }
            
            // 如果需要关闭连接
            if (result.isShouldClose()) {
                logger.info("Closing connection due to handling result: {}", 
                           ctx.channel().remoteAddress());
                ctx.close();
            }
            
        } catch (Exception e) {
            logger.error("Failed to handle message: messageType={}, from={}", 
                        message.getMsgType(), ctx.channel().remoteAddress(), e);
            
            sendErrorResponse(ctx, message.getOperationID(), "消息处理异常");
        }
    }
    
    /**
     * 解析WebSocket消息
     */
    private WSMessage parseMessage(String messageText) {
        try {
            return objectMapper.readValue(messageText, WSMessage.class);
        } catch (Exception e) {
            logger.error("Failed to parse message: {}", messageText, e);
            return null;
        }
    }
    
    /**
     * 发送消息到客户端
     */
    private void sendMessage(ChannelHandlerContext ctx, WSMessage message) {
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            ctx.writeAndFlush(new TextWebSocketFrame(messageJson));
            
            logger.debug("Sent message to {}: {}", ctx.channel().remoteAddress(), messageJson);
            
        } catch (Exception e) {
            logger.error("Failed to send message to {}", ctx.channel().remoteAddress(), e);
        }
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, String operationID, String errorMessage) {
        WSMessage errorResp = WSMessage.errorResp(operationID, 500, errorMessage);
        sendMessage(ctx, errorResp);
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
