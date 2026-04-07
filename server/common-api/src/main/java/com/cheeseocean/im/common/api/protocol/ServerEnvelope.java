package com.cheeseocean.im.common.api.protocol;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.enums.CommandType;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class ServerEnvelope implements Serializable {

    private static final long serialVersionUID = 1L;

    private CommandType command;
    private String      requestId;
    private Object      body;

    public static ServerEnvelope of(CommandType command, String requestId, Object body) {
        ServerEnvelope envelope = new ServerEnvelope();
        envelope.setCommand(command);
        envelope.setRequestId(requestId);
        envelope.setBody(body);
        return envelope;
    }

    public static ServerEnvelope connect(String requestId, Object body) {
        return of(CommandType.CONNECT, requestId, body);
    }

    public static ServerEnvelope auth(String requestId, Object body) {
        return of(CommandType.AUTH, requestId, body);
    }

    public static ServerEnvelope heartbeat(String requestId, Object body) {
        return of(CommandType.HEARTBEAT, requestId, body);
    }

    public static ServerEnvelope chatSend(String requestId, Object body) {
        return of(CommandType.CHAT_SEND, requestId, body);
    }

    public static ServerEnvelope error(String requestId, Object body) {
        return of(CommandType.ERROR, requestId, body);
    }

    public static ServerEnvelope error(String requestId, int code, String message) {
        return error(requestId, Map.of("code", code, "message", message));
    }

    public static ServerEnvelope chatRecv(String requestId, DispatchPayload payload) {
        return of(CommandType.CHAT_RECV, requestId, payload);
    }

    public static ServerEnvelope forceLogout(String requestId, String reason) {
        return of(CommandType.FORCE_LOGOUT, requestId, Map.of(
                "reason", reason,
                "message", "您的账号在其他设备登录，已被强制下线"));
    }
}
