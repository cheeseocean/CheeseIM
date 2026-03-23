package com.cheeseocean.im.common.core.enums;

import java.util.Arrays;

public enum ContentType {
    TEXT(101),
    IMAGE(102),
    VOICE(103),
    VIDEO(104),
    FILE(105),
    LOCATION(106),
    CUSTOM(200),
    READ_RECEIPT(2004),
    REVOKE_NOTIFY(2005),
    TYPING(4002),
    SYSTEM_NOTIFY(7001),
    FORCE_LOGOUT(7002);

    private final int code;

    ContentType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ContentType fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ContentType code: " + code));
    }
}
