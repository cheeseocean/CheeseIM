package com.cheeseocean.im.postoffice.server;

import com.cheeseocean.im.common.core.util.IdGenerator;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.common.core.enums.ConnectionState;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.handler.MessageHandler;
import com.cheeseocean.im.postoffice.handler.MessageHandlerFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import com.cheeseocean.im.common.core.logging.CommonLoggers;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket服务器处理器
 * 处理WebSocket连接的生命周期和消息处理
 * 
 * @author xxxcrel
 */
@Component
@ChannelHandler.Sharable
public class WebSocketServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    
    private static final Logger logger = CommonLoggers.POSTOFFICE;
    
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
            
            ClientEnvelope envelope = parseMessage(messageText);
            if (envelope == null) {
                sendErrorResponse(ctx, "system", "消息格式错误");
                return;
            }
            
            UserConnection connection = connectionManager.getConnectionByChannel(ctx.channel());
            if (connection == null) {
                logger.warn("Connection not found for channel: {}", ctx.channel().remoteAddress());
                sendErrorResponse(ctx, envelope.getRequestId(), "连接不存在");
                return;
            }
            
            connection.updateLastActiveTime();
            connection.incrementRecvMsg();
            
            handleMessage(ctx, connection, envelope);
            
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
            connection.setProtocol("WebSocket");
            connection.setStatus(UserConnection.STATUS_CONNECTED);
            ConnectionContext connectionContext = new ConnectionContext();
            connectionContext.setConnId(connectionID);
            connectionContext.setConnectedAt(connection.getConnectTime());
            connectionContext.setLastHeartbeatAt(connection.getLastActiveTime());
            connectionContext.setRemoteIp(clientIP);
            connectionContext.setState(ConnectionState.PENDING);
            connection.setContext(connectionContext);

            // 先注册为未认证连接，认证成功后再提升为已认证连接
            if (!connectionManager.registerPendingConnection(connection)) {
                logger.warn("Failed to register pending connection: connectionID={}, remoteAddress={}",
                        connectionID, ctx.channel().remoteAddress());
                ctx.close();
                return;
            }

            logger.info("WebSocket handshake complete: connectionID={}, clientIP={}, userAgent={}",
                    connectionID, clientIP, connection.getUserAgent());

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
    private void handleMessage(ChannelHandlerContext ctx, UserConnection connection, ClientEnvelope envelope) {
        CommandType commandType = envelope.getCommand();
        try {
            if (commandType == CommandType.CONNECT) {
                sendMessage(ctx, buildConnectSuccessEnvelope(envelope.getRequestId(), connection));
                return;
            }

            MessageHandler handler = messageHandlerFactory.getHandler(commandType);
            if (handler == null) {
                logger.warn("Unsupported command type: {} from {}",
                        commandType, ctx.channel().remoteAddress());
                
                sendErrorResponse(ctx, envelope.getRequestId(),
                        "不支持的消息类型: " + commandType);
                return;
            }
            
            MessageHandler.HandleResult result = handler.handle(connection, envelope);
            
            if (result.getResponseEnvelope() != null) {
                sendMessage(ctx, result.getResponseEnvelope());
            }
            
            if (!result.isSuccess()) {
                logger.warn("Message handling failed: commandType={}, error={}, from={}",
                        commandType, result.getErrorMessage(), ctx.channel().remoteAddress());
            }
            
            if (result.isShouldClose()) {
                logger.info("Closing connection due to handling result: {}", 
                           ctx.channel().remoteAddress());
                ctx.close();
            }
            
        } catch (Exception e) {
            logger.error("Failed to handle message: commandType={}, from={}",
                    commandType, ctx.channel().remoteAddress(), e);
            
            sendErrorResponse(ctx, envelope.getRequestId(), "消息处理异常");
        }
    }
    
    /**
     * 解析WebSocket消息
     */
    private ClientEnvelope parseMessage(String messageText) {
        try {
            Map<String, Object> raw = objectMapper.readValue(
                    messageText,
                    new TypeReference<Map<String, Object>>() {}
            );
            CommandType command = parseCommand(raw.get("command"));
            if (command == null) {
                return null;
            }

            ClientEnvelope envelope = new ClientEnvelope();
            envelope.setCommand(command);
            envelope.setRequestId(raw.get("requestId") == null ? "system" : String.valueOf(raw.get("requestId")));
            envelope.setBody(raw.get("body"));
            return envelope;
        } catch (Exception e) {
            logger.error("Failed to parse message: {}", messageText, e);
            return null;
        }
    }
    
    /**
     * 发送消息到客户端
     */
    private void sendMessage(ChannelHandlerContext ctx, ServerEnvelope envelope) {
        try {
            String messageJson = objectMapper.writeValueAsString(serializeEnvelope(envelope));
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
        ServerEnvelope envelope = ServerEnvelope.error(
                operationID == null || operationID.isBlank() ? "system" : operationID,
                500,
                errorMessage
        );
        sendMessage(ctx, envelope);
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

    private CommandType parseCommand(Object rawCommand) {
        if (rawCommand instanceof Number) {
            try {
                return CommandType.fromCode(((Number) rawCommand).intValue());
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        if (rawCommand instanceof String) {
            String commandValue = ((String) rawCommand).trim();
            if (commandValue.isEmpty()) {
                return null;
            }
            try {
                return CommandType.fromCode(Integer.parseInt(commandValue));
            } catch (NumberFormatException ignored) {
                try {
                    return CommandType.valueOf(commandValue);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    private ServerEnvelope buildConnectSuccessEnvelope(String requestId, UserConnection connection) {
        return ServerEnvelope.connect(
                requestId == null || requestId.isBlank() ? "system" : requestId,
                Map.of(
                "connId", connection.getConnectionID(),
                "message", "连接成功"
        ));
    }

    private Map<String, Object> serializeEnvelope(ServerEnvelope envelope) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("command", envelope.getCommand() == null ? null : envelope.getCommand().getCode());
        payload.put("requestId", envelope.getRequestId());
        payload.put("body", envelope.getBody());
        return payload;
    }
}
