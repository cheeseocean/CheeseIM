package com.cheeseocean.im.common.api.protocol;

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
}
