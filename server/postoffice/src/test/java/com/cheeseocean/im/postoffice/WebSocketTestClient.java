package com.cheeseocean.im.postoffice;

import com.cheeseocean.im.common.core.enums.CommandType;
import com.cheeseocean.im.postoffice.client.ProtocolContractFixtures;
import com.fasterxml.jackson.core.type.TypeReference;
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
        String successJson = ProtocolContractFixtures.wsConnectSuccessJson();
        Map<String, Object> envelope = objectMapper.readValue(successJson, new TypeReference<Map<String, Object>>() {});

        assertEquals(CommandType.CONNECT.getCode(), Integer.parseInt(String.valueOf(envelope.get("command"))));
        assertEquals("system", envelope.get("requestId"));
        assertEquals(ProtocolContractFixtures.CONNECT_SUCCESS_MESSAGE, ((Map<?, ?>) envelope.get("body")).get("message"));
    }

    @Test
    void shouldSerializeCanonicalAuthRequestAndResponses() throws Exception {
        String requestJson = ProtocolContractFixtures.wsAuthRequestJson();
        String successJson = ProtocolContractFixtures.wsAuthSuccessJson();
        String failedJson = ProtocolContractFixtures.wsAuthFailedJson();
        Map<String, Object> requestEnvelope = objectMapper.readValue(requestJson, new TypeReference<Map<String, Object>>() {});
        Map<String, Object> successEnvelope = objectMapper.readValue(successJson, new TypeReference<Map<String, Object>>() {});
        Map<String, Object> failedEnvelope = objectMapper.readValue(failedJson, new TypeReference<Map<String, Object>>() {});

        assertEquals(CommandType.AUTH.getCode(), Integer.parseInt(String.valueOf(requestEnvelope.get("command"))));
        assertEquals(ProtocolContractFixtures.TOKEN, ((Map<?, ?>) requestEnvelope.get("body")).get("token"));
        assertEquals(
                objectMapper.readTree(objectMapper.writeValueAsString(ProtocolContractFixtures.wsAuthSuccessPayload())),
                objectMapper.readTree(objectMapper.writeValueAsString(successEnvelope.get("body")))
        );
        assertEquals(CommandType.AUTH.getCode(), Integer.parseInt(String.valueOf(successEnvelope.get("command"))));
        assertEquals(CommandType.ERROR.getCode(), Integer.parseInt(String.valueOf(failedEnvelope.get("command"))));
        assertEquals(ProtocolContractFixtures.AUTH_FAILED_REASON, ((Map<?, ?>) failedEnvelope.get("body")).get("message"));
    }

    @Test
    void shouldSerializeCanonicalSendRequestAndAck() throws Exception {
        String requestJson = ProtocolContractFixtures.wsSendRequestJson();
        String responseJson = ProtocolContractFixtures.wsSendResponseAckJson();
        Map<String, Object> requestEnvelope = objectMapper.readValue(requestJson, new TypeReference<Map<String, Object>>() {});
        Map<String, Object> responseEnvelope = objectMapper.readValue(responseJson, new TypeReference<Map<String, Object>>() {});

        assertEquals(CommandType.CHAT_SEND.getCode(), Integer.parseInt(String.valueOf(requestEnvelope.get("command"))));
        assertEquals(ProtocolContractFixtures.CLIENT_MSG_ID, ((Map<?, ?>) requestEnvelope.get("body")).get("clientMsgID"));
        assertEquals(
                objectMapper.readTree(objectMapper.writeValueAsString(ProtocolContractFixtures.wsSendResponsePayload())),
                objectMapper.readTree(objectMapper.writeValueAsString(responseEnvelope.get("body")))
        );
        assertEquals(CommandType.CHAT_SEND.getCode(), Integer.parseInt(String.valueOf(responseEnvelope.get("command"))));
    }

    @Test
    void shouldSerializeCanonicalInboundNotify() throws Exception {
        String json = ProtocolContractFixtures.wsRecvNotifyJson();
        Map<String, Object> envelope = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

        assertEquals(CommandType.CHAT_RECV.getCode(), Integer.parseInt(String.valueOf(envelope.get("command"))));
        assertEquals(
                objectMapper.readTree(objectMapper.writeValueAsString(ProtocolContractFixtures.wsRecvNotifyPayload())),
                objectMapper.readTree(objectMapper.writeValueAsString(envelope.get("body")))
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
                        Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {});
                        handleServerMessage(envelope);
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

        sendEnvelope(CommandType.AUTH, generateOperationID(), authData);
        System.out.println("Authentication request sent: userID=" + userID + ", platformID=" + platformID);
    }

    private static void sendHeartbeat() throws Exception {
        sendEnvelope(CommandType.HEARTBEAT, generateOperationID(), "ping");
        System.out.println("Heartbeat sent");
    }

    private static void sendChatMessage(String recvID, String content) throws Exception {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("clientMsgID", "client_" + System.currentTimeMillis());
        msgData.put("recvID", recvID);
        msgData.put("content", content);
        msgData.put("contentType", 101);
        msgData.put("sessionType", 1);

        sendEnvelope(CommandType.CHAT_SEND, generateOperationID(), msgData);
        System.out.println("Chat message sent to " + recvID + ": " + content);
    }

    private static void sendEnvelope(CommandType command, String requestId, Object body) throws Exception {
        client.send(objectMapper.writeValueAsString(ProtocolContractFixtures.clientEnvelope(command, requestId, body)));
    }

    private static void handleServerMessage(Map<String, Object> message) {
        Number command = (Number) message.get("command");
        Object body = message.get("body");
        int commandCode = command == null ? -1 : command.intValue();

        if (commandCode == CommandType.CONNECT.getCode()) {
                System.out.println("Connection established successfully");
        } else if (commandCode == CommandType.AUTH.getCode()) {
                System.out.println("Authentication successful");
        } else if (commandCode == CommandType.ERROR.getCode()) {
                System.out.println("Server error: " + body);
        } else if (commandCode == CommandType.HEARTBEAT.getCode()) {
                System.out.println("Heartbeat response received");
        } else if (commandCode == CommandType.CHAT_SEND.getCode()) {
                System.out.println("Message sent successfully: " + body);
        } else if (commandCode == CommandType.CHAT_RECV.getCode()) {
                System.out.println("New message received: " + body);
        } else {
            System.out.println("Server message [" + command + "]: " + body);
        }
    }

    private static String generateOperationID() {
        return "test_" + System.currentTimeMillis();
    }
}
