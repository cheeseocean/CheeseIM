package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 通用错误码枚举。
 *
 * @author xxxcrel
 */
public enum ErrorCode implements IEnum {
    /** 成功。 */
    SUCCESS(0, "成功"),
    /** 参数非法。 */
    INVALID_PARAM(1001, "参数非法"),
    /** 用户不存在。 */
    USER_NOT_FOUND(1002, "用户不存在"),
    /** Token 无效。 */
    TOKEN_INVALID(1003, "Token 无效"),
    /** 消息发送失败。 */
    MSG_SEND_FAILED(1004, "消息发送失败"),
    /** 系统内部错误。 */
    INTERNAL_ERROR(1005, "系统内部错误"),
    /** 请求频率超过服务保护阈值。 */
    RATE_LIMITED(1006, "请求过于频繁"),
    /** 幂等键已被同一请求使用。 */
    IDEMPOTENCY_CONFLICT(1007, "幂等键对应的请求内容冲突"),
    /** 相同幂等请求正在由其它执行者处理。 */
    MESSAGE_IN_PROGRESS(1008, "消息正在处理"),
    /** 登录身份凭据缺失、无效或已被重放。 */
    AUTHENTICATION_FAILED(1009, "身份认证失败"),
    /** 群不存在。 */
    GROUP_NOT_FOUND(1101, "群不存在"),
    /** 群已解散或封禁。 */
    GROUP_UNAVAILABLE(1102, "群当前不可用"),
    /** 发送者不是群成员。 */
    GROUP_NOT_MEMBER(1103, "发送者不是群成员"),
    /** 发送者处于群禁言状态。 */
    GROUP_MEMBER_MUTED(1104, "发送者处于禁言状态");

    private final int code;
    private final String desc;

    ErrorCode(int code, String desc) {
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

    public static ErrorCode fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ErrorCode code: " + code));
    }
}
