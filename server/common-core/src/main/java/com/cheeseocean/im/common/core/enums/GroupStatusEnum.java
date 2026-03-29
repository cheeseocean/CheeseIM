package com.cheeseocean.im.common.core.enums;

/**
 * 群组状态枚举。
 *
 * <p>对应 MongoDB group 集合中的 status 字段。
 */
public enum GroupStatusEnum {

    /** 正常运营 */
    NORMAL(0, "正常"),
    /** 已解散 */
    DISBANDED(1, "已解散"),
    /** 已封禁，不可发言 */
    BANNED(2, "已封禁");

    private final int code;
    private final String desc;

    GroupStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据 code 反查枚举，找不到时抛出异常。
     */
    public static GroupStatusEnum fromCode(int code) {
        for (GroupStatusEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("非法群组状态码: " + code);
    }
}
