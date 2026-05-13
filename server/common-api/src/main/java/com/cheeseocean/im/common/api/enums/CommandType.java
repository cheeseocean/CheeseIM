package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 客户端命令类型枚举。
 *
 * @author xxxcrel
 */
public enum CommandType implements IEnum {
    /** 建立连接。 */
    CONNECT(1, "建立连接"),
    /** 用户认证。 */
    AUTH(10, "用户认证"),
    /** 心跳保活。 */
    HEARTBEAT(20, "心跳保活"),
    /** 发送聊天消息。 */
    CHAT_SEND(30, "发送聊天消息"),
    /** 消息发送响应（服务端收到消息后回复）。 */
    CHAT_SEND_ACK(31, "消息发送响应"),
    /** 接收聊天消息。 */
    CHAT_RECV(32, "接收聊天消息"),
    /** 已读回执。 */
    CHAT_READ(33, "已读回执"),
    /** 撤回聊天消息。 */
    CHAT_REVOKE(34, "撤回聊天消息"),
    /** 强制下线通知。 */
    FORCE_LOGOUT(35, "强制下线通知"),
    /** 错误响应。 */
    ERROR(90, "错误响应");

    private final int code;
    private final String desc;

    CommandType(int code, String desc) {
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

    public static CommandType fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown CommandType code: " + code));
    }
}
