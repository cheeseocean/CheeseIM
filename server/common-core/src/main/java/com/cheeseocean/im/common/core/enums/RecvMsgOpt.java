package com.cheeseocean.im.common.core.enums;

import java.util.Arrays;

/**
 * 消息接收选项
 *
 * <ul>
 *   <li>{@link #RECEIVE}            (0) — 正常接收：投递并推送</li>
 *   <li>{@link #NOT_RECEIVE}        (1) — 屏蔽：直接丢弃消息</li>
 *   <li>{@link #RECEIVE_NOT_NOTIFY} (2) — 勿扰：投递但不发离线推送</li>
 * </ul>
 *
 * 同时用于全局设置（{@code globalRecvMsgOpt}）和会话级设置（{@code recvMsgOpt}）。
 * 数字 code 用于 MongoDB 持久化和消息传输；应用逻辑中统一使用此枚举比较。
 */
public enum RecvMsgOpt {

    RECEIVE(0),
    NOT_RECEIVE(1),
    RECEIVE_NOT_NOTIFY(2);

    private final int code;

    RecvMsgOpt(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static RecvMsgOpt fromCode(int code) {
        return Arrays.stream(values())
                .filter(v -> v.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的 RecvMsgOpt code: " + code));
    }
}
