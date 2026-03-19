package com.cheeseocean.im.postoffice;

import com.cheeseocean.im.postoffice.client.ProtocolContractFixtures;
import com.cheeseocean.im.postoffice.protocol.WSMessage;
import com.cheeseocean.im.postoffice.protocol.WSMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSocket测试客户端
 * 用于测试CheeseIM Postoffice Gateway的WebSocket功能
 */
public class WebSocketTestClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static WebSocketClient client;
    private static final CountDownLatch connectLatch = new CountDownLatch(1);

    @Test
    void shouldSerializeCanonicalConnectSuccessPush() throws Exception {
        WSMessage success = ProtocolContractFixtures.wsConnectSuccessPush();
        String successJson = objectMapper.writeValueAsString(success);

        assertTrue(successJson.contains("\"msgType\":1002"));
        assertTrue(successJson.contains("\"operationID\":\"system\""));
        assertTrue(successJson.contains(ProtocolContractFixtures.CONNECT_SUCCESS_MESSAGE));
    }

    @Test
    void shouldSerializeCanonicalAuthRequestAndResponses() throws Exception {
        WSMessage authRequest = ProtocolContractFixtures.wsAuthRequest();
        WSMessage authSuccess = ProtocolContractFixtures.wsAuthSuccessResponse();
        WSMessage authFailed = ProtocolContractFixtures.wsAuthFailedResponse();

        String requestJson = objectMapper.writeValueAsString(authRequest);
        String successJson = objectMapper.writeValueAsString(authSuccess);
        String failedJson = objectMapper.writeValueAsString(authFailed);

        assertTrue(requestJson.contains("\"msgType\":1101"));
        assertTrue(requestJson.contains("\"token\":\"" + ProtocolContractFixtures.TOKEN + "\""));
        assertEquals(
                objectMapper.readTree(objectMapper.writeValueAsString(ProtocolContractFixtures.wsAuthSuccessPayload())),
                objectMapper.readTree(objectMapper.writeValueAsString(authSuccess.getData()))
        );
        assertTrue(successJson.contains("\"msgType\":1102"));
        assertTrue(failedJson.contains("\"msgType\":1103"));
        assertTrue(failedJson.contains(ProtocolContractFixtures.AUTH_FAILED_REASON));
    }

    @Test
    void shouldSerializeCanonicalSendRequestAndAck() throws Exception {
        WSMessage sendRequest = ProtocolContractFixtures.wsSendRequest();
        WSMessage sendResponse = ProtocolContractFixtures.wsSendResponseAck();

        String requestJson = objectMapper.writeValueAsString(sendRequest);
        String responseJson = objectMapper.writeValueAsString(sendResponse);

        assertTrue(requestJson.contains("\"msgType\":2001"));
        assertTrue(requestJson.contains("\"clientMsgID\":\"" + ProtocolContractFixtures.CLIENT_MSG_ID + "\""));
        assertEquals(
                objectMapper.readTree(objectMapper.writeValueAsString(ProtocolContractFixtures.wsSendResponsePayload())),
                objectMapper.readTree(objectMapper.writeValueAsString(sendResponse.getData()))
        );
        assertTrue(responseJson.contains("\"msgType\":2002"));
    }

    @Test
    void shouldSerializeCanonicalInboundNotify() throws Exception {
        WSMessage notify = ProtocolContractFixtures.wsRecvNotify();
        String json = objectMapper.writeValueAsString(notify);

        assertTrue(json.contains("\"msgType\":2003"));
        assertEquals(
                objectMapper.readTree(objectMapper.writeValueAsString(ProtocolContractFixtures.wsRecvNotifyPayload())),
                objectMapper.readTree(objectMapper.writeValueAsString(notify.getData()))
        );
    }

    public static void main(String[] args) {
        try {
            String serverUrl = "ws://localhost:5147/ws";
            URI serverUri = new URI(serverUrl);

            client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("Connected to server: " + serverUrl);
                    System.out.println("HTTP Status: " + handshake.getHttpStatus());
                    connectLatch.countDown();
                }

                @Override
                public void onMessage(String message) {
                    System.out.println("Received: " + message);
                    try {
                        WSMessage wsMessage = objectMapper.readValue(message, WSMessage.class);
                        handleServerMessage(wsMessage);
                    } catch (Exception e) {
                        System.err.println("Failed to parse message: " + e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("Connection closed: " + reason + " (code: " + code + ")");
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("Connection error: " + ex.getMessage());
                }
            };

            System.out.println("Connecting to " + serverUrl + "...");
            client.connect();
            connectLatch.await();
            startInteractiveMode();
        } catch (Exception e) {
            System.err.println("Failed to start test client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startInteractiveMode() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\nInteractive Mode Started");
        System.out.println("Available commands:");
        System.out.println("  auth <userID> <platformID> <token> - Authenticate user");
        System.out.println("  heartbeat - Send heartbeat");
        System.out.println("  send <recvID> <content> - Send message");
        System.out.println("  help - Show this help");
        System.out.println("  quit - Exit");
        System.out.println();

        while (true) {
            System.out.print("Enter command: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            String[] parts = input.split("\\s+");
            String command = parts[0].toLowerCase();

            try {
                switch (command) {
                    case "auth":
                        if (parts.length >= 4) {
                            sendAuthMessage(parts[1], Integer.parseInt(parts[2]), parts[3]);
                        } else {
                            System.out.println("Usage: auth <userID> <platformID> <token>");
                        }
                        break;
                    case "heartbeat":
                        sendHeartbeat();
                        break;
                    case "send":
                        if (parts.length >= 3) {
                            String recvID = parts[1];
                            String content = String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length));
                            sendChatMessage(recvID, content);
                        } else {
                            System.out.println("Usage: send <recvID> <content>");
                        }
                        break;
                    case "help":
                        System.out.println("Available commands:");
                        System.out.println("  auth <userID> <platformID> <token> - Authenticate user");
                        System.out.println("  heartbeat - Send heartbeat");
                        System.out.println("  send <recvID> <content> - Send message");
                        System.out.println("  help - Show this help");
                        System.out.println("  quit - Exit");
                        break;
                    case "quit":
                    case "exit":
                        System.out.println("Goodbye!");
                        client.close();
                        System.exit(0);
                        break;
                    default:
                        System.out.println("Unknown command: " + command + ". Type 'help' for available commands.");
                        break;
                }
            } catch (Exception e) {
                System.err.println("Command execution failed: " + e.getMessage());
            }
        }
    }

    private static void sendAuthMessage(String userID, int platformID, String token) throws Exception {
        Map<String, Object> authData = new HashMap<>();
        authData.put("token", token);
        authData.put("userID", userID);
        authData.put("platformID", platformID);

        sendMessage(new WSMessage(WSMessageType.WS_AUTH_REQ, generateOperationID(), authData));
        System.out.println("Authentication request sent: userID=" + userID + ", platformID=" + platformID);
    }

    private static void sendHeartbeat() throws Exception {
        sendMessage(new WSMessage(WSMessageType.WS_HEARTBEAT_REQ, generateOperationID(), "ping"));
        System.out.println("Heartbeat sent");
    }

    private static void sendChatMessage(String recvID, String content) throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("clientMsgID", "client_" + System.currentTimeMillis());
        msgData.put("recvID", recvID);
        msgData.put("content", content);
        msgData.put("contentType", 101);
        msgData.put("sessionType", 1);

        sendMessage(new WSMessage(WSMessageType.WS_SEND_MSG_REQ, generateOperationID(), msgData));
        System.out.println("Chat message sent to " + recvID + ": " + content);
    }

    private static void sendMessage(WSMessage message) throws Exception {
        client.send(objectMapper.writeValueAsString(message));
    }

    private static void handleServerMessage(WSMessage message) {
        int msgType = message.getMsgType();

        switch (msgType) {
            case WSMessageType.WS_CONNECT_SUCCESS:
                System.out.println("Connection established successfully");
                break;
            case WSMessageType.WS_AUTH_SUCCESS:
                System.out.println("Authentication successful");
                break;
            case WSMessageType.WS_AUTH_FAILED:
                System.out.println("Authentication failed: " + message.getData());
                break;
            case WSMessageType.WS_HEARTBEAT_RESP:
                System.out.println("Heartbeat response received");
                break;
            case WSMessageType.WS_SEND_MSG_RESP:
                System.out.println("Message sent successfully: " + message.getData());
                break;
            case WSMessageType.WS_RECV_MSG_NOTIFY:
                System.out.println("New message received: " + message.getData());
                break;
            case WSMessageType.WS_ERROR_RESP:
                System.out.println("Server error: " + message.getData());
                break;
            default:
                System.out.println("Server message [" + msgType + "]: " + message.getData());
                break;
        }
    }

    private static String generateOperationID() {
        return "test_" + System.currentTimeMillis();
    }
}
