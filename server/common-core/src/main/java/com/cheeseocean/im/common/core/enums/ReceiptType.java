package com.cheeseocean.im.common.core.enums;

import java.util.Arrays;

public enum ReceiptType {
    DELIVERED("DELIVERED"),
    RECEIVED("RECEIVED"),
    READ_CURSOR("READ_CURSOR"),
    READ("READ");

    private final String code;

    ReceiptType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ReceiptType fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ReceiptType code: " + code));
    }
}
