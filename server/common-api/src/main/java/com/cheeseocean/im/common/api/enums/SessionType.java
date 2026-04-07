package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 会话类型枚举。
 *
 * @author xxxcrel
 */
public enum SessionType implements IEnum {
    /** 单聊会话。 */
    SINGLE(1, "单聊"),
    /** 群聊会话。 */
    GROUP(2, "群聊"),
    /** 通知会话。 */
    NOTIFICATION(3, "通知");

    private final int code;
    private final String desc;

    SessionType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    public static SessionType fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown SessionType code: " + code));
    }
}
