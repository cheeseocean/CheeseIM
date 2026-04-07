package com.cheeseocean.im.common.api.enums;

import java.util.Arrays;

/**
 * 消息内容类型枚举。
 *
 * @author xxxcrel
 */
public enum ContentType implements IEnum {
    /** 文本消息。 */
    TEXT(101, "文本"),
    /** 图片消息。 */
    IMAGE(102, "图片"),
    /** 语音消息。 */
    VOICE(103, "语音"),
    /** 视频消息。 */
    VIDEO(104, "视频"),
    /** 文件消息。 */
    FILE(105, "文件"),
    /** 位置消息。 */
    LOCATION(106, "位置"),
    /** 自定义消息。 */
    CUSTOM(200, "自定义"),
    /** 已读回执。 */
    READ_RECEIPT(2004, "已读回执"),
    /** 撤回通知。 */
    REVOKE_NOTIFY(2005, "撤回通知"),
    /** 正在输入提示。 */
    TYPING(4002, "正在输入"),
    /** 系统通知。 */
    SYSTEM_NOTIFY(7001, "系统通知"),
    /** 强制下线通知。 */
    FORCE_LOGOUT(7002, "强制下线");

    private final int code;
    private final String desc;

    ContentType(int code, String desc) {
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

    public static ContentType fromCode(int code) {
        return Arrays.stream(values())
                .filter(value -> value.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ContentType code: " + code));
    }
}
