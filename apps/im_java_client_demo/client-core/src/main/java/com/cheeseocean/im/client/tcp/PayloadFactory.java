package com.cheeseocean.im.client.tcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class PayloadFactory {

    public static final int CONTENT_TYPE_TEXT = 101;
    public static final int CONTENT_TYPE_READ_RECEIPT = 2004;
    public static final int CONTENT_TYPE_REVOKE_NOTIFY = 2005;
    public static final int CONTENT_TYPE_TYPING = 4002;
    public static final int SESSION_TYPE_SINGLE = 1;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String authPayload(String ticket) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket", ticket);
        return toJson(payload);
    }

    public String singleChatTextPayload(String clientMsgId, String peerUserId, String text) {
        return singleChatPayload(clientMsgId, peerUserId, text, CONTENT_TYPE_TEXT);
    }

    public String singleChatPayload(String clientMsgId, String peerUserId, String content, int contentType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientMsgID", clientMsgId);
        payload.put("recvID", peerUserId);
        payload.put("content", content);
        payload.put("contentType", contentType);
        payload.put("sessionType", SESSION_TYPE_SINGLE);
        return toJson(payload);
    }

    public String readReceiptPayload(String clientMsgId, String peerUserId, long seq) {
        return singleChatPayload(clientMsgId, peerUserId, String.valueOf(seq), CONTENT_TYPE_READ_RECEIPT);
    }

    public String revokeNotifyPayload(String clientMsgId, String peerUserId, String serverMsgId) {
        return singleChatPayload(clientMsgId, peerUserId, serverMsgId, CONTENT_TYPE_REVOKE_NOTIFY);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to build tcp payload", e);
        }
    }
}
