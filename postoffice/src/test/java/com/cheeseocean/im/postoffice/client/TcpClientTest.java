package com.cheeseocean.im.postoffice.client;

import com.cheeseocean.im.postoffice.protocol.CheeseMessage;
import com.cheeseocean.im.postoffice.protocol.CheeseMessageType;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;

/**
 * TCP客户端测试工具
 * 用于测试TCP自定义协议的连接和消息收发
 * 
 * @author CheeseIM
 */
public class TcpClientTest {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpClientTest.class);
    
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8081;
    
    @Test
    public void testTcpConnection() throws Exception {
        logger.info("Starting TCP client test...");
        
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            logger.info("Connected to TCP server: {}:{}", SERVER_HOST, SERVER_PORT);
            
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            
            // 测试连接
            testConnection(out, in);
            
            // 测试认证
            testAuthentication(out, in);
            
            // 测试心跳
            testHeartbeat(out, in);
            
            // 测试发送消息
            testSendMessage(out, in);
            
            logger.info("TCP client test completed successfully");
            
        } catch (Exception e) {
            logger.error("TCP client test failed", e);
            throw e;
        }
    }
    
    /**
     * 测试连接
     */
    private void testConnection(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing connection...");
        
        // 发送连接请求
        CheeseMessage connectReq = new CheeseMessage(CheeseMessageType.TCP_CONNECT_REQ,
                                              UUID.randomUUID().toString(), 
                                              "{}");
        sendMessage(out, connectReq);
        
        // 接收连接响应
        CheeseMessage response = receiveMessage(in);
        logger.info("Received connection response: {}", response);
        
        if (response.getMsgType() != CheeseMessageType.TCP_CONNECT_SUCCESS) {
            throw new RuntimeException("Connection failed: " + response.getData());
        }
        
        logger.info("Connection test passed");
    }
    
    /**
     * 测试认证
     */
    private void testAuthentication(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing authentication...");
        
        // 发送认证请求
        String authData = "{\"token\":\"test-token\",\"userID\":\"test-user\",\"platformID\":2}";
        CheeseMessage authReq = new CheeseMessage(CheeseMessageType.TCP_AUTH_REQ,
                                           UUID.randomUUID().toString(), 
                                           authData);
        sendMessage(out, authReq);
        
        // 接收认证响应
        CheeseMessage response = receiveMessage(in);
        logger.info("Received auth response: {}", response);
        
        // 注意：这里可能会收到认证失败的响应，因为我们使用的是测试token
        // 在实际测试中，需要使用有效的token
        
        logger.info("Authentication test completed");
    }
    
    /**
     * 测试心跳
     */
    private void testHeartbeat(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing heartbeat...");
        
        // 发送心跳请求
        CheeseMessage heartbeatReq = new CheeseMessage(CheeseMessageType.TCP_HEARTBEAT_REQ,
                                                UUID.randomUUID().toString(), 
                                                "ping");
        sendMessage(out, heartbeatReq);
        
        // 接收心跳响应
        CheeseMessage response = receiveMessage(in);
        logger.info("Received heartbeat response: {}", response);
        
        if (response.getMsgType() != CheeseMessageType.TCP_HEARTBEAT_RESP) {
            throw new RuntimeException("Heartbeat failed: " + response.getData());
        }
        
        logger.info("Heartbeat test passed");
    }
    
    /**
     * 测试发送消息
     */
    private void testSendMessage(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing send message...");
        
        // 发送消息请求
        String msgData = "{\"content\":\"Hello TCP Server!\",\"contentType\":101,\"recvID\":\"receiver-123\"}";
        CheeseMessage sendMsgReq = new CheeseMessage(CheeseMessageType.TCP_SEND_MSG_REQ,
                                              UUID.randomUUID().toString(), 
                                              msgData);
        sendMessage(out, sendMsgReq);
        
        // 接收发送消息响应
        CheeseMessage response = receiveMessage(in);
        logger.info("Received send message response: {}", response);
        
        logger.info("Send message test completed");
    }
    
    /**
     * 发送TCP消息
     */
    private void sendMessage(OutputStream out, CheeseMessage message) throws IOException {
        byte[] messageBytes = message.encode();
        out.write(messageBytes);
        out.flush();
        
        logger.debug("Sent TCP message: msgType={}, operationID={}, dataLength={}", 
                    message.getMsgType(), message.getOperationID(), message.getDataLength());
    }
    
    /**
     * 接收TCP消息
     */
    private CheeseMessage receiveMessage(InputStream in) throws IOException {
        // 读取消息头部
        byte[] headerBytes = new byte[CheeseMessage.HEADER_LENGTH];
        int bytesRead = 0;
        while (bytesRead < CheeseMessage.HEADER_LENGTH) {
            int read = in.read(headerBytes, bytesRead, CheeseMessage.HEADER_LENGTH - bytesRead);
            if (read == -1) {
                throw new IOException("Connection closed while reading header");
            }
            bytesRead += read;
        }
        
        // 解析头部获取数据长度
        // Magic (2) + Version (1) + MsgType (1) + Length (4) = 8 bytes
        int dataLength = ((headerBytes[4] & 0xFF) << 24) |
                        ((headerBytes[5] & 0xFF) << 16) |
                        ((headerBytes[6] & 0xFF) << 8) |
                        (headerBytes[7] & 0xFF);
        
        // 读取数据部分
        byte[] dataBytes = new byte[dataLength];
        if (dataLength > 0) {
            bytesRead = 0;
            while (bytesRead < dataLength) {
                int read = in.read(dataBytes, bytesRead, dataLength - bytesRead);
                if (read == -1) {
                    throw new IOException("Connection closed while reading data");
                }
                bytesRead += read;
            }
        }
        
        // 组合完整消息
        byte[] fullMessage = new byte[CheeseMessage.HEADER_LENGTH + dataLength];
        System.arraycopy(headerBytes, 0, fullMessage, 0, CheeseMessage.HEADER_LENGTH);
        if (dataLength > 0) {
            System.arraycopy(dataBytes, 0, fullMessage, CheeseMessage.HEADER_LENGTH, dataLength);
        }
        
        // 解码消息
        CheeseMessage message = CheeseMessage.decode(fullMessage);
        
        logger.debug("Received TCP message: msgType={}, operationID={}, dataLength={}", 
                    message.getMsgType(), message.getOperationID(), message.getDataLength());
        
        return message;
    }
    
    /**
     * 简单的TCP客户端示例（可以独立运行）
     */
    public static void main(String[] args) {
        TcpClientTest client = new TcpClientTest();
        try {
            client.testTcpConnection();
        } catch (Exception e) {
            logger.error("TCP client test failed", e);
        }
    }
}
