package com.cheeseocean.im.common.api.protocol;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.core.enums.CommandType;

import java.io.Serializable;

public class ServerEnvelope implements Serializable {

    private static final long serialVersionUID = 1L;

    private CommandType command;
    private String requestId;
    private Object body;

    public CommandType getCommand() {
        return command;
    }

    public void setCommand(CommandType command) {
        this.command = command;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public static ServerEnvelope chatRecv(String requestId, DispatchPayload payload) {
        ServerEnvelope envelope = new ServerEnvelope();
        envelope.setCommand(CommandType.CHAT_RECV);
        envelope.setRequestId(requestId);
        envelope.setBody(payload);
        return envelope;
    }

    public static ServerEnvelope forceLogout(String requestId, String reason) {
        ServerEnvelope envelope = new ServerEnvelope();
        envelope.setCommand(CommandType.FORCE_LOGOUT);
        envelope.setRequestId(requestId);
        envelope.setBody(java.util.Map.of(
                "reason", reason,
                "message", "您的账号在其他设备登录，已被强制下线"));
        return envelope;
    }
}
