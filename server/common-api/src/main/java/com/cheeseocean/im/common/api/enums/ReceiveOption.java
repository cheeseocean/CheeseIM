package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 消息接收选项
 *
 * <ul>
 *   <li>{@link #RECEIVE}            (0) — 正常接收：投递并推送</li>
 *   <li>{@link #BLOCK}        (1) — 屏蔽：直接丢弃消息</li>
 *   <li>{@link #DO_NOT_DISTURB} (2) — 勿扰：投递但不发离线推送</li>
 * </ul>
 *
 * 同时用于用户全局设置（{@code user.receiveOptions}）和会话级设置（{@code converstaion.receiveOptions}）。
 * 数字 code 用于 MongoDB 持久化和消息传输；应用逻辑中统一使用此枚举比较。
 *
 * @author xxxcrel
 */
public enum ReceiveOption implements IEnum {

    /** 正常接收消息。 */
    RECEIVE(0, "正常接收"),
    /** 屏蔽消息。 */
    BLOCK(1, "屏蔽"),
    /** 接收但不提醒。 */
    DO_NOT_DISTURB(2, "勿扰");

    private final int code;
    private final String desc;

    ReceiveOption(int code, String desc) {
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

    public static ReceiveOption fromCode(int code) {
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的 ReceiveOption code: " + code));
    }
}
