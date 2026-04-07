package com.cheeseocean.im.common.api.enums;

/**
 * 入群验证方式枚举。
 *
 * <p>群主/管理员可在群设置中配置，控制新成员加群的审核流程。
 *
 * @author xxxcrel
 */
public enum NeedVerificationEnum implements IEnum {

    /** 无需验证，任何人可直接加入 */
    NONE(0, "无需验证"),
    /** 需要管理员验证审批 */
    REQUIRED(1, "需要验证"),
    /** 完全禁止，只能由管理员邀请 */
    FORBIDDEN(2, "禁止加入");

    private final int code;
    private final String desc;

    NeedVerificationEnum(int code, String desc) {
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
    public static NeedVerificationEnum fromCode(int code) {
        for (NeedVerificationEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("非法入群验证方式码: " + code);
    }
}
