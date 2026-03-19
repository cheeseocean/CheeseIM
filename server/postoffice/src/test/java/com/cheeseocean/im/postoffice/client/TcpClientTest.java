package com.cheeseocean.im.postoffice.client;

import com.cheeseocean.im.postoffice.protocol.CheeseMessage;
import com.cheeseocean.im.postoffice.protocol.CheeseMessageType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TCP客户端测试工具
 * 用于测试TCP自定义协议的连接和消息收发
 */
public class TcpClientTest {

    private static final Logger logger = LoggerFactory.getLogger(TcpClientTest.class);

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 5148;

    @Test
    void shouldEncodeCanonicalTcpConnectSuccessPush() {
        CheeseMessage decodedSuccess = CheeseMessage.decode(ProtocolContractFixtures.tcpConnectSuccessPush().encode());
        assertEquals(CheeseMessageType.TCP_CONNECT_SUCCESS, decodedSuccess.getMsgType());
        assertEquals(ProtocolContractFixtures.CONNECT_OPERATION_ID, decodedSuccess.getOperationID().trim());
        assertEquals(ProtocolContractFixtures.CONNECT_SUCCESS_MESSAGE, decodedSuccess.getData());
    }

    @Test
    void shouldEncodeCanonicalTcpAuthRequest() {
        CheeseMessage decoded = CheeseMessage.decode(ProtocolContractFixtures.tcpAuthRequest().encode());

        assertEquals(CheeseMessageType.TCP_AUTH_REQ, decoded.getMsgType());
        assertEquals(ProtocolContractFixtures.tcpAuthRequestJson(), decoded.getData());
    }

    @Test
    void shouldEncodeCanonicalTcpAuthResponses() {
        CheeseMessage decodedSuccess = CheeseMessage.decode(ProtocolContractFixtures.tcpAuthSuccessResponse().encode());
        CheeseMessage decodedFailed = CheeseMessage.decode(ProtocolContractFixtures.tcpAuthFailedResponse().encode());

        assertEquals(CheeseMessageType.TCP_AUTH_SUCCESS, decodedSuccess.getMsgType());
        assertEquals(ProtocolContractFixtures.tcpAuthSuccessJson(), decodedSuccess.getData());
        assertEquals(CheeseMessageType.TCP_AUTH_FAILED, decodedFailed.getMsgType());
        assertEquals(ProtocolContractFixtures.AUTH_FAILED_REASON, decodedFailed.getData());
    }

    @Test
    void shouldEncodeCanonicalTcpSendRequestAndAck() {
        CheeseMessage decodedRequest = CheeseMessage.decode(ProtocolContractFixtures.tcpSendRequest().encode());
        CheeseMessage decodedResponse = CheeseMessage.decode(ProtocolContractFixtures.tcpSendResponseAck().encode());

        assertEquals(CheeseMessageType.TCP_SEND_MSG_REQ, decodedRequest.getMsgType());
        assertEquals(ProtocolContractFixtures.tcpSendRequestJson(), decodedRequest.getData());
        assertEquals(CheeseMessageType.TCP_SEND_MSG_RESP, decodedResponse.getMsgType());
        assertEquals(ProtocolContractFixtures.tcpSendResponseJson(), decodedResponse.getData());
    }

    @Test
    void shouldEncodeCanonicalTcpRecvNotify() {
        CheeseMessage decoded = CheeseMessage.decode(ProtocolContractFixtures.tcpInboundNotify().encode());

        assertEquals(CheeseMessageType.TCP_RECV_MSG_NOTIFY, decoded.getMsgType());
        assertEquals(ProtocolContractFixtures.tcpRecvNotifyJson(), decoded.getData());
    }

    @Test
    @Disabled("Requires a running local TCP server on localhost:5148")
    public void testTcpConnection() throws Exception {
        logger.info("Starting TCP client test...");

        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT)) {
            logger.info("Connected to TCP server: {}:{}", SERVER_HOST, SERVER_PORT);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            testConnection(out, in);
            testAuthentication(out, in);
            testHeartbeat(out, in);
            testSendMessage(out, in);

            logger.info("TCP client test completed successfully");
        } catch (Exception e) {
            logger.error("TCP client test failed", e);
            throw e;
        }
    }

    private void testConnection(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing connection...");

        CheeseMessage response = receiveMessage(in);
        logger.info("Received initial connect push: {}", response);

        if (response.getMsgType() != CheeseMessageType.TCP_CONNECT_SUCCESS
                || !"system".equals(response.getOperationID().trim())) {
            throw new RuntimeException("Unexpected connect push: " + response);
        }
    }

    private void testAuthentication(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing authentication...");

        sendMessage(out, new CheeseMessage(
                CheeseMessageType.TCP_AUTH_REQ,
                UUID.randomUUID().toString(),
                ProtocolContractFixtures.tcpAuthRequestJson()
        ));

        CheeseMessage response = receiveMessage(in);
        logger.info("Received auth response: {}", response);
    }

    private void testHeartbeat(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing heartbeat...");

        sendMessage(out, new CheeseMessage(
                CheeseMessageType.TCP_HEARTBEAT_REQ,
                UUID.randomUUID().toString(),
                "ping"
        ));

        CheeseMessage response = receiveMessage(in);
        logger.info("Received heartbeat response: {}", response);

        if (response.getMsgType() != CheeseMessageType.TCP_HEARTBEAT_RESP) {
            throw new RuntimeException("Heartbeat failed: " + response.getData());
        }
    }

    private void testSendMessage(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing send message...");

        sendMessage(out, new CheeseMessage(
                CheeseMessageType.TCP_SEND_MSG_REQ,
                UUID.randomUUID().toString(),
                ProtocolContractFixtures.tcpSendRequestJson()
        ));

        CheeseMessage response = receiveMessage(in);
        logger.info("Received send message response: {}", response);
    }

    private void sendMessage(OutputStream out, CheeseMessage message) throws IOException {
        byte[] messageBytes = message.encode();
        out.write(messageBytes);
        out.flush();

        logger.debug("Sent TCP message: msgType={}, operationID={}, dataLength={}",
                message.getMsgType(), message.getOperationID(), message.getDataLength());
    }

    private CheeseMessage receiveMessage(InputStream in) throws IOException {
        byte[] headerBytes = new byte[CheeseMessage.HEADER_LENGTH];
        int bytesRead = 0;
        while (bytesRead < CheeseMessage.HEADER_LENGTH) {
            int read = in.read(headerBytes, bytesRead, CheeseMessage.HEADER_LENGTH - bytesRead);
            if (read == -1) {
                throw new IOException("Connection closed while reading header");
            }
            bytesRead += read;
        }

        int dataLength = ((headerBytes[4] & 0xFF) << 24)
                | ((headerBytes[5] & 0xFF) << 16)
                | ((headerBytes[6] & 0xFF) << 8)
                | (headerBytes[7] & 0xFF);

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

        byte[] fullMessage = new byte[CheeseMessage.HEADER_LENGTH + dataLength];
        System.arraycopy(headerBytes, 0, fullMessage, 0, CheeseMessage.HEADER_LENGTH);
        if (dataLength > 0) {
            System.arraycopy(dataBytes, 0, fullMessage, CheeseMessage.HEADER_LENGTH, dataLength);
        }

        CheeseMessage message = CheeseMessage.decode(fullMessage);

        logger.debug("Received TCP message: msgType={}, operationID={}, dataLength={}",
                message.getMsgType(), message.getOperationID(), message.getDataLength());

        return message;
    }

    public static void main(String[] args) {
        TcpClientTest client = new TcpClientTest();
        try {
            client.testTcpConnection();
        } catch (Exception e) {
            logger.error("TCP client test failed", e);
        }
    }

}
