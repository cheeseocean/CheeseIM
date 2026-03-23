package com.cheeseocean.im.common.core.enums;

import java.util.Arrays;

public enum ErrorCode {
    SUCCESS(0),
    INVALID_PARAM(1001),
    USER_NOT_FOUND(1002),
    TOKEN_INVALID(1003),
    MSG_SEND_FAILED(1004),
    INTERNAL_ERROR(1005);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ErrorCode fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ErrorCode code: " + code));
    }
}
