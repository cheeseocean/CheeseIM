package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * DLT 重放审计状态。
 *
 * <p>状态持久化到 Mongo，code 必须保持稳定。</p>
 */
public enum DltRedriveStatus {

    PROCESSING(1, "处理中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "失败");

    private final int code;
    private final String desc;

    DltRedriveStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static DltRedriveStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown DLT redrive status code: " + code));
    }
}
