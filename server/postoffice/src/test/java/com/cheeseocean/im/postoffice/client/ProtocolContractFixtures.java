package com.cheeseocean.im.postoffice.client;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.protocol.ClientEnvelope;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.enums.CommandType;
import com.cheeseocean.im.common.core.util.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical protocol fixtures shared by TCP and WebSocket contract tests.
 */
public final class ProtocolContractFixtures {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createDefaultMapper();
    public static final short TCP_MAGIC = (short) 0xCEEE;
    public static final byte TCP_VERSION = 0x01;
    public static final int TCP_HEADER_LENGTH = 32;
    public static final byte TCP_CONNECT_SUCCESS = 2;
    public static final byte TCP_AUTH_REQ = 10;
    public static final byte TCP_AUTH_SUCCESS = 11;
    public static final byte TCP_AUTH_FAILED = 12;
    public static final byte TCP_HEARTBEAT_REQ = 20;
    public static final byte TCP_HEARTBEAT_RESP = 21;
    public static final byte TCP_SEND_MSG_REQ = 30;
    public static final byte TCP_SEND_MSG_RESP = 31;
    public static final byte TCP_RECV_MSG_NOTIFY = 32;
    public static final byte TCP_MSG_READ_RECEIPT = 33;

    public static final String CONNECT_OPERATION_ID = "system";
    public static final String AUTH_OPERATION_ID = "op-auth-00000001";
    public static final String SEND_OPERATION_ID = "op-send-00000001";
    public static final String NOTIFY_OPERATION_ID = "op-notify-00001";

    public static final String USER_ID = "user123";
    public static final int PLATFORM_ID = 2;
    public static final String TOKEN = "jwt-token";
    public static final String PEER_USER_ID = "receiver123";
    public static final String CLIENT_MSG_ID = "client-123";
    public static final String SERVER_MSG_ID = "msg-456";
    public static final long SEND_TIME = 1710000000000L;

    public static final String CONNECT_SUCCESS_MESSAGE = "连接成功";
    public static final String AUTH_FAILED_REASON = "token invalid";

    private ProtocolContractFixtures() {
    }

    public static String tcpAuthRequestJson() {
        return "{\"token\":\"" + TOKEN + "\",\"userID\":\"" + USER_ID + "\",\"platformID\":" + PLATFORM_ID + "}";
    }

    public static String tcpAuthSuccessJson() {
        return "{\"userID\":\"" + USER_ID + "\",\"message\":\"认证成功\"}";
    }

    public static String tcpSendRequestJson() {
        return "{\"clientMsgID\":\"" + CLIENT_MSG_ID + "\",\"recvID\":\"" + PEER_USER_ID
                + "\",\"content\":\"Hello World!\",\"contentType\":101,\"sessionType\":1}";
    }

    public static String tcpSendResponseJson() {
        return "{\"serverMsgID\":\"" + SERVER_MSG_ID + "\",\"clientMsgID\":\"" + CLIENT_MSG_ID
                + "\",\"sendTime\":" + SEND_TIME + "}";
    }

    public static String tcpRecvNotifyJson() {
        return "{\"serverMsgID\":\"" + SERVER_MSG_ID + "\",\"clientMsgID\":\"" + CLIENT_MSG_ID
                + "\",\"sendID\":\"" + PEER_USER_ID + "\",\"recvID\":\"" + USER_ID
                + "\",\"content\":\"Hello World!\",\"contentType\":101,\"sessionType\":1,\"sendTime\":" + SEND_TIME + "}";
    }

    public static byte[] tcpAuthRequestBytes() {
        return tcpFrame(TCP_AUTH_REQ, AUTH_OPERATION_ID, tcpAuthRequestJson());
    }

    public static byte[] tcpConnectSuccessBytes() {
        return tcpFrame(TCP_CONNECT_SUCCESS, CONNECT_OPERATION_ID, CONNECT_SUCCESS_MESSAGE);
    }

    public static byte[] tcpAuthSuccessBytes() {
        return tcpFrame(TCP_AUTH_SUCCESS, AUTH_OPERATION_ID, tcpAuthSuccessJson());
    }

    public static byte[] tcpAuthFailedBytes() {
        return tcpFrame(TCP_AUTH_FAILED, AUTH_OPERATION_ID, AUTH_FAILED_REASON);
    }

    public static byte[] tcpSendRequestBytes() {
        return tcpFrame(TCP_SEND_MSG_REQ, SEND_OPERATION_ID, tcpSendRequestJson());
    }

    public static byte[] tcpSendResponseAckBytes() {
        return tcpFrame(TCP_SEND_MSG_RESP, SEND_OPERATION_ID, tcpSendResponseJson());
    }

    public static byte[] tcpInboundNotifyBytes() {
        return tcpFrame(TCP_RECV_MSG_NOTIFY, NOTIFY_OPERATION_ID, tcpRecvNotifyJson());
    }

    public static Map<String, Object> wsAuthPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", TOKEN);
        payload.put("userID", USER_ID);
        payload.put("platformID", PLATFORM_ID);
        return payload;
    }

    public static Map<String, Object> wsAuthSuccessPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userID", USER_ID);
        payload.put("message", "认证成功");
        return payload;
    }

    public static Map<String, Object> wsSendPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientMsgID", CLIENT_MSG_ID);
        payload.put("recvID", PEER_USER_ID);
        payload.put("content", "Hello World!");
        payload.put("contentType", 101);
        payload.put("sessionType", 1);
        return payload;
    }

    public static Map<String, Object> wsSendResponsePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverMsgID", SERVER_MSG_ID);
        payload.put("clientMsgID", CLIENT_MSG_ID);
        payload.put("sendTime", SEND_TIME);
        return payload;
    }

    public static Map<String, Object> wsRecvNotifyPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverMsgID", SERVER_MSG_ID);
        payload.put("clientMsgID", CLIENT_MSG_ID);
        payload.put("sendID", PEER_USER_ID);
        payload.put("recvID", USER_ID);
        payload.put("content", "Hello World!");
        payload.put("contentType", 101);
        payload.put("sessionType", 1);
        payload.put("sendTime", SEND_TIME);
        return payload;
    }

    public static String wsAuthRequestJson() throws Exception {
        return OBJECT_MAPPER.writeValueAsString(serializeEnvelope(clientEnvelope(CommandType.AUTH, AUTH_OPERATION_ID, wsAuthPayload())));
    }

    public static String wsConnectSuccessJson() throws Exception {
        return OBJECT_MAPPER.writeValueAsString(serializeEnvelope(serverEnvelope(CommandType.CONNECT, CONNECT_OPERATION_ID, Map.of("message", CONNECT_SUCCESS_MESSAGE))));
    }

    public static String wsAuthSuccessJson() throws Exception {
        return OBJECT_MAPPER.writeValueAsString(serializeEnvelope(serverEnvelope(CommandType.AUTH, AUTH_OPERATION_ID, wsAuthSuccessPayload())));
    }

    public static String wsAuthFailedJson() throws Exception {
        return OBJECT_MAPPER.writeValueAsString(serializeEnvelope(serverEnvelope(CommandType.ERROR, AUTH_OPERATION_ID, Map.of("message", AUTH_FAILED_REASON))));
    }

    public static String wsSendRequestJson() throws Exception {
        return OBJECT_MAPPER.writeValueAsString(serializeEnvelope(clientEnvelope(CommandType.CHAT_SEND, SEND_OPERATION_ID, wsSendPayload())));
    }

    public static String wsSendResponseAckJson() throws Exception {
        return OBJECT_MAPPER.writeValueAsString(serializeEnvelope(serverEnvelope(CommandType.CHAT_SEND, SEND_OPERATION_ID, wsSendResponsePayload())));
    }

    public static String wsRecvNotifyJson() throws Exception {
        return OBJECT_MAPPER.writeValueAsString(serializeEnvelope(serverEnvelope(CommandType.CHAT_RECV, NOTIFY_OPERATION_ID, wsRecvNotifyPayload())));
    }

    public static RawTcpFrame decodeTcpFrame(byte[] bytes) {
        if (bytes.length < TCP_HEADER_LENGTH) {
            throw new IllegalArgumentException("Invalid message length: " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        short magic = buffer.getShort();
        if (magic != TCP_MAGIC) {
            throw new IllegalArgumentException("Invalid magic: " + magic);
        }
        byte version = buffer.get();
        byte msgType = buffer.get();
        int dataLength = buffer.getInt();
        byte[] operationIdBytes = new byte[16];
        buffer.get(operationIdBytes);
        String requestId = new String(operationIdBytes, StandardCharsets.UTF_8).trim();
        long timestamp = buffer.getLong();
        byte[] dataBytes = new byte[dataLength];
        buffer.get(dataBytes);
        return new RawTcpFrame(version, msgType, requestId, timestamp, new String(dataBytes, StandardCharsets.UTF_8));
    }

    public static byte[] tcpFrameForTest(byte msgType, String requestId, String data) {
        return tcpFrame(msgType, requestId, data);
    }

    public static ClientEnvelope clientEnvelope(CommandType command, String requestId, Object body) {
        ClientEnvelope envelope = new ClientEnvelope();
        envelope.setCommand(command);
        envelope.setRequestId(requestId);
        envelope.setBody(body);
        return envelope;
    }

    public static ServerEnvelope serverEnvelope(CommandType command, String requestId, Object body) {
        return ServerEnvelope.of(command, requestId, body);
    }

    public static DispatchPayload recvDispatchPayload() {
        DispatchPayload payload = new DispatchPayload();
        Message msg = new Message();
        msg.setServerMsgId(SERVER_MSG_ID);
        msg.setClientMsgId(CLIENT_MSG_ID);
        msg.setContent("Hello World!");
        msg.setContentType(101);
        msg.setSeq(1L);
        msg.setSendTime(SEND_TIME);
        payload.setMsg(msg);
        return payload;
    }

    private static byte[] tcpFrame(byte msgType, String requestId, String data) {
        byte[] dataBytes = data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8);
        byte[] requestBytes = requestId == null ? new byte[0] : requestId.getBytes(StandardCharsets.UTF_8);
        byte[] fixedRequestId = new byte[16];
        System.arraycopy(requestBytes, 0, fixedRequestId, 0, Math.min(requestBytes.length, 16));
        ByteBuffer buffer = ByteBuffer.allocate(TCP_HEADER_LENGTH + dataBytes.length);
        buffer.putShort(TCP_MAGIC);
        buffer.put(TCP_VERSION);
        buffer.put(msgType);
        buffer.putInt(dataBytes.length);
        buffer.put(fixedRequestId);
        buffer.putLong(SEND_TIME);
        buffer.put(dataBytes);
        return buffer.array();
    }

    private static Map<String, Object> serializeEnvelope(Object envelope) {
        if (envelope instanceof ClientEnvelope clientEnvelope) {
            return Map.of(
                    "command", clientEnvelope.getCommand().getCode(),
                    "requestId", clientEnvelope.getRequestId(),
                    "body", clientEnvelope.getBody()
            );
        }
        ServerEnvelope serverEnvelope = (ServerEnvelope) envelope;
        return Map.of(
                "command", serverEnvelope.getCommand().getCode(),
                "requestId", serverEnvelope.getRequestId(),
                "body", serverEnvelope.getBody()
        );
    }

    public record RawTcpFrame(byte version, byte msgType, String requestId, long timestamp, String data) {
    }
}
