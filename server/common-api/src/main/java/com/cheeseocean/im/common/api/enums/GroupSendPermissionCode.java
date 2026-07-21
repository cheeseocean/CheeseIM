package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 群消息发送权限结果。
 *
 * <p>该 code 跨 Dubbo 传输并用于审计，必须保持稳定，不得按枚举 ordinal 序列化。</p>
 */
public enum GroupSendPermissionCode implements IEnum {

    ALLOWED(0, "允许发送"),
    INVALID_REQUEST(1, "请求参数非法"),
    GROUP_NOT_FOUND(2, "群不存在"),
    GROUP_DISBANDED(3, "群已解散"),
    GROUP_BANNED(4, "群已封禁"),
    NOT_MEMBER(5, "发送者不是群成员"),
    MEMBER_MUTED(6, "发送者处于禁言状态");

    private final int code;
    private final String desc;

    GroupSendPermissionCode(int code, String desc) {
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

    public static GroupSendPermissionCode fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown GroupSendPermissionCode code: " + code));
    }
}
