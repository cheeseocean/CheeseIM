package com.cheeseocean.im.postoffice.client;

import com.cheeseocean.im.postoffice.protocol.CheeseMessage;
import com.cheeseocean.im.postoffice.protocol.CheeseMessageType;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.protocol.WSMessageType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical protocol fixtures shared by TCP and WebSocket contract tests.
 */
public final class ProtocolContractFixtures {

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

    public static CheeseMessage tcpAuthRequest() {
        return new CheeseMessage(CheeseMessageType.TCP_AUTH_REQ, AUTH_OPERATION_ID, tcpAuthRequestJson());
    }

    public static CheeseMessage tcpConnectSuccessPush() {
        return CheeseMessage.connectSuccess(CONNECT_OPERATION_ID);
    }

    public static CheeseMessage tcpAuthSuccessResponse() {
        return CheeseMessage.authSuccess(AUTH_OPERATION_ID, USER_ID);
    }

    public static CheeseMessage tcpAuthFailedResponse() {
        return CheeseMessage.authFailed(AUTH_OPERATION_ID, AUTH_FAILED_REASON);
    }

    public static CheeseMessage tcpSendRequest() {
        return new CheeseMessage(CheeseMessageType.TCP_SEND_MSG_REQ, SEND_OPERATION_ID, tcpSendRequestJson());
    }

    public static CheeseMessage tcpSendResponseAck() {
        return new CheeseMessage(CheeseMessageType.TCP_SEND_MSG_RESP, SEND_OPERATION_ID, tcpSendResponseJson());
    }

    public static CheeseMessage tcpInboundNotify() {
        return new CheeseMessage(CheeseMessageType.TCP_RECV_MSG_NOTIFY, NOTIFY_OPERATION_ID, tcpRecvNotifyJson());
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

    public static WSMessage wsAuthRequest() {
        return new WSMessage(WSMessageType.WS_AUTH_REQ, AUTH_OPERATION_ID, wsAuthPayload());
    }

    public static WSMessage wsConnectSuccessPush() {
        return WSMessage.connectSuccess(CONNECT_OPERATION_ID);
    }

    public static WSMessage wsAuthSuccessResponse() {
        return WSMessage.authSuccess(AUTH_OPERATION_ID, USER_ID);
    }

    public static WSMessage wsAuthFailedResponse() {
        return WSMessage.authFailed(AUTH_OPERATION_ID, AUTH_FAILED_REASON);
    }

    public static WSMessage wsSendRequest() {
        return new WSMessage(WSMessageType.WS_SEND_MSG_REQ, SEND_OPERATION_ID, wsSendPayload());
    }

    public static WSMessage wsSendResponseAck() {
        return WSMessage.sendMsgResp(SEND_OPERATION_ID, SERVER_MSG_ID, CLIENT_MSG_ID, SEND_TIME);
    }

    public static WSMessage wsRecvNotify() {
        return WSMessage.recvMsgNotify(NOTIFY_OPERATION_ID, wsRecvNotifyPayload());
    }
}
