package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 消息来源枚举。
 *
 * <p>用于标识消息由哪一侧或哪一类角色发起，数字 code 可用于存储和传输。
 *
 * @author xxxcrel
 */
public enum MessageSource implements IEnum {

    /** 普通用户发送。 */
    USER(0, "用户"),
    /** 系统账号或系统流程发送。 */
    SYSTEM(1, "系统"),
    /** 管理员发送。 */
    ADMIN(2, "管理员");

    private final int code;
    private final String desc;

    MessageSource(int code, String desc) {
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

    public static MessageSource fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的 MessageFrom code: " + code));
    }
}
