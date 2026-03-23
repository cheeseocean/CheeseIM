package com.cheeseocean.im.common.core.enums;

import java.util.Arrays;

public enum SessionType {
    SINGLE(1),
    GROUP(2),
    NOTIFICATION(3);

    private final int code;

    SessionType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static SessionType fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown SessionType code: " + code));
    }
}
