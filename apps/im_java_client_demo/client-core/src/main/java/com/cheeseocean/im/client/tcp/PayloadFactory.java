package com.cheeseocean.im.client.tcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class PayloadFactory {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String authPayload(String token, String userId, Integer platformId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("userID", userId);
        payload.put("platformID", platformId);
        return toJson(payload);
    }

    public String singleChatTextPayload(String clientMsgId, String peerUserId, String text) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientMsgID", clientMsgId);
        payload.put("recvID", peerUserId);
        payload.put("content", text);
        payload.put("contentType", 101);
        payload.put("sessionType", 1);
        return toJson(payload);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to build tcp payload", e);
        }
    }
}
