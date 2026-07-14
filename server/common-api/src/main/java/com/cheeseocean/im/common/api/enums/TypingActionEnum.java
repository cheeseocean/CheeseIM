package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 输入中状态动作。
 *
 * <p>START 必须携带短 TTL，STOP 立即清除客户端展示状态。两者均为可重复投递的幂等信号。
 */
public enum TypingActionEnum implements IEnum {
    /** 开始输入。 */
    START(1, "开始输入"),
    /** 停止输入。 */
    STOP(2, "停止输入");

    private final int code;
    private final String desc;

    TypingActionEnum(int code, String desc) {
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

    public static TypingActionEnum fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知输入中动作: " + code));
    }
}
