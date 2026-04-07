package com.cheeseocean.im.common.api.enums;

/**
 * 群 @ 强提醒类型枚举。
 *
 * <p>记录在会话文档中，用于客户端决定是否展示 "有人@我" 或 "@全体成员" 角标。
 *
 * @author xxxcrel
 */
public enum GroupAtTypeEnum implements IEnum {

    /** 无 @ 提醒 */
    NONE(0, "无"),
    /** @全体成员 */
    AT_ALL(1, "@全体成员"),
    /** @我（当前用户） */
    AT_ME(2, "@我"),
    /** @我 且 @全体成员 */
    AT_ALL_AND_ME(3, "@全体成员且@我");

    private final int code;
    private final String desc;

    GroupAtTypeEnum(int code, String desc) {
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
    public static GroupAtTypeEnum fromCode(int code) {
        for (GroupAtTypeEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("非法群 @ 类型码: " + code);
    }
}
