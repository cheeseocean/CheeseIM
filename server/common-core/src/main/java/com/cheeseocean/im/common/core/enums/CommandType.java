package com.cheeseocean.im.common.core.enums;

import java.util.Arrays;

public enum CommandType {
    CONNECT(1),
    AUTH(10),
    HEARTBEAT(20),
    CHAT_SEND(30),
    CHAT_RECV(32),
    CHAT_REVOKE(34),
    ERROR(90);

    private final int code;

    CommandType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static CommandType fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown CommandType code: " + code));
    }
}
