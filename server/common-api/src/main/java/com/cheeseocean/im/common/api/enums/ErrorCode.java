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
    IDEMPOTENCY_CONFLICT(1007, "请求正在处理或已处理");

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
