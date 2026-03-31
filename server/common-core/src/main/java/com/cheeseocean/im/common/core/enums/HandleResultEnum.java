package com.cheeseocean.im.common.core.enums;

/**
 * 申请处理结果枚举。
 *
 * <p>通用于好友申请（FriendApplication）和群组申请（GroupRequest）。
 * 存储整数 code，应用逻辑使用 {@link #fromCode(int)} 转换。
 */
public enum HandleResultEnum {

    /** 待处理，申请方等待对方响应 */
    PENDING(0, "待处理"),
    /** 已同意 */
    ACCEPTED(1, "已同意"),
    /** 已拒绝 */
    REJECTED(-1, "已拒绝");

    private final int code;
    private final String desc;

    HandleResultEnum(int code, String desc) {
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
    public static HandleResultEnum fromCode(int code) {
        for (HandleResultEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("非法申请处理结果码: " + code);
    }
}
