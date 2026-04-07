package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 消息状态枚举。
 *
 * <p>用于描述消息在业务链路中的当前状态，数字 code 可用于存储和传输。
 *
 * @author xxxcrel
 */
public enum MessageStatus implements IEnum {

    /**
     * 已创建但尚未进入后续处理链路。
     */
    CREATED(0, "已创建"),
    /**
     * 已进入系统并被受理。
     */
    ACCEPTED(1, "已受理"),
    /**
     * 已完成投递。
     */
    DELIVERED(2, "已投递"),
    /**
     * 被过滤。
     */
    FILTERED(3, "已过滤"),
    /**
     * 处理或投递失败。
     */
    FAILED(4, "失败");

    private final int code;
    private final String desc;

    MessageStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static MessageStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的 MessageStatus code: " + code));
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
