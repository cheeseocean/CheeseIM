package com.cheeseocean.im.postoffice.client;

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
        ProtocolContractFixtures.RawTcpFrame decodedSuccess =
                ProtocolContractFixtures.decodeTcpFrame(ProtocolContractFixtures.tcpConnectSuccessBytes());
        assertEquals(ProtocolContractFixtures.TCP_CONNECT_SUCCESS, decodedSuccess.msgType());
        assertEquals(ProtocolContractFixtures.CONNECT_OPERATION_ID, decodedSuccess.requestId());
        assertEquals(ProtocolContractFixtures.CONNECT_SUCCESS_MESSAGE, decodedSuccess.data());
    }

    @Test
    void shouldEncodeCanonicalTcpAuthRequest() {
        ProtocolContractFixtures.RawTcpFrame decoded =
                ProtocolContractFixtures.decodeTcpFrame(ProtocolContractFixtures.tcpAuthRequestBytes());

        assertEquals(ProtocolContractFixtures.TCP_AUTH_REQ, decoded.msgType());
        assertEquals(ProtocolContractFixtures.tcpAuthRequestJson(), decoded.data());
    }

    @Test
    void shouldEncodeCanonicalTcpAuthResponses() {
        ProtocolContractFixtures.RawTcpFrame decodedSuccess =
                ProtocolContractFixtures.decodeTcpFrame(ProtocolContractFixtures.tcpAuthSuccessBytes());
        ProtocolContractFixtures.RawTcpFrame decodedFailed =
                ProtocolContractFixtures.decodeTcpFrame(ProtocolContractFixtures.tcpAuthFailedBytes());

        assertEquals(ProtocolContractFixtures.TCP_AUTH_SUCCESS, decodedSuccess.msgType());
        assertEquals(ProtocolContractFixtures.tcpAuthSuccessJson(), decodedSuccess.data());
        assertEquals(ProtocolContractFixtures.TCP_AUTH_FAILED, decodedFailed.msgType());
        assertEquals(ProtocolContractFixtures.AUTH_FAILED_REASON, decodedFailed.data());
    }

    @Test
    void shouldEncodeCanonicalTcpSendRequestAndAck() {
        ProtocolContractFixtures.RawTcpFrame decodedRequest =
                ProtocolContractFixtures.decodeTcpFrame(ProtocolContractFixtures.tcpSendRequestBytes());
        ProtocolContractFixtures.RawTcpFrame decodedResponse =
                ProtocolContractFixtures.decodeTcpFrame(ProtocolContractFixtures.tcpSendResponseAckBytes());

        assertEquals(ProtocolContractFixtures.TCP_SEND_MSG_REQ, decodedRequest.msgType());
        assertEquals(ProtocolContractFixtures.tcpSendRequestJson(), decodedRequest.data());
        assertEquals(ProtocolContractFixtures.TCP_SEND_MSG_RESP, decodedResponse.msgType());
        assertEquals(ProtocolContractFixtures.tcpSendResponseJson(), decodedResponse.data());
    }

    @Test
    void shouldEncodeCanonicalTcpRecvNotify() {
        ProtocolContractFixtures.RawTcpFrame decoded =
                ProtocolContractFixtures.decodeTcpFrame(ProtocolContractFixtures.tcpInboundNotifyBytes());

        assertEquals(ProtocolContractFixtures.TCP_RECV_MSG_NOTIFY, decoded.msgType());
        assertEquals(ProtocolContractFixtures.tcpRecvNotifyJson(), decoded.data());
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

        ProtocolContractFixtures.RawTcpFrame response = receiveMessage(in);
        logger.info("Received initial connect push: {}", response);

        if (response.msgType() != ProtocolContractFixtures.TCP_CONNECT_SUCCESS
                || !"system".equals(response.requestId())) {
            throw new RuntimeException("Unexpected connect push: " + response);
        }
    }

    private void testAuthentication(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing authentication...");

        sendMessage(out, ProtocolContractFixtures.decodeTcpFrame(
                ProtocolContractFixtures.tcpFrameForTest(ProtocolContractFixtures.TCP_AUTH_REQ,
                        UUID.randomUUID().toString(),
                        ProtocolContractFixtures.tcpAuthRequestJson())));

        ProtocolContractFixtures.RawTcpFrame response = receiveMessage(in);
        logger.info("Received auth response: {}", response);
    }

    private void testHeartbeat(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing heartbeat...");

        sendMessage(out, ProtocolContractFixtures.decodeTcpFrame(
                ProtocolContractFixtures.tcpFrameForTest(ProtocolContractFixtures.TCP_HEARTBEAT_REQ,
                        UUID.randomUUID().toString(),
                        "ping")));

        ProtocolContractFixtures.RawTcpFrame response = receiveMessage(in);
        logger.info("Received heartbeat response: {}", response);

        if (response.msgType() != ProtocolContractFixtures.TCP_HEARTBEAT_RESP) {
            throw new RuntimeException("Heartbeat failed: " + response.data());
        }
    }

    private void testSendMessage(OutputStream out, InputStream in) throws Exception {
        logger.info("Testing send message...");

        sendMessage(out, ProtocolContractFixtures.decodeTcpFrame(
                ProtocolContractFixtures.tcpFrameForTest(ProtocolContractFixtures.TCP_SEND_MSG_REQ,
                        UUID.randomUUID().toString(),
                        ProtocolContractFixtures.tcpSendRequestJson())));

        ProtocolContractFixtures.RawTcpFrame response = receiveMessage(in);
        logger.info("Received send message response: {}", response);
    }

    private void sendMessage(OutputStream out, ProtocolContractFixtures.RawTcpFrame message) throws IOException {
        byte[] messageBytes = ProtocolContractFixtures.tcpFrameForTest(
                message.msgType(),
                message.requestId(),
                message.data()
        );
        out.write(messageBytes);
        out.flush();

        logger.debug("Sent TCP message: msgType={}, requestId={}, dataLength={}",
                message.msgType(), message.requestId(), message.data() == null ? 0 : message.data().length());
    }

    private ProtocolContractFixtures.RawTcpFrame receiveMessage(InputStream in) throws IOException {
        byte[] headerBytes = new byte[ProtocolContractFixtures.TCP_HEADER_LENGTH];
        int bytesRead = 0;
        while (bytesRead < ProtocolContractFixtures.TCP_HEADER_LENGTH) {
            int read = in.read(headerBytes, bytesRead, ProtocolContractFixtures.TCP_HEADER_LENGTH - bytesRead);
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

        byte[] fullMessage = new byte[ProtocolContractFixtures.TCP_HEADER_LENGTH + dataLength];
        System.arraycopy(headerBytes, 0, fullMessage, 0, ProtocolContractFixtures.TCP_HEADER_LENGTH);
        if (dataLength > 0) {
            System.arraycopy(dataBytes, 0, fullMessage, ProtocolContractFixtures.TCP_HEADER_LENGTH, dataLength);
        }

        ProtocolContractFixtures.RawTcpFrame message = ProtocolContractFixtures.decodeTcpFrame(fullMessage);

        logger.debug("Received TCP message: msgType={}, requestId={}, dataLength={}",
                message.msgType(), message.requestId(), message.data() == null ? 0 : message.data().length());

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
