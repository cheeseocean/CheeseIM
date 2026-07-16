package com.cheeseocean.im.common.api.enums;

/**
 * 会话控制事件类型。
 *
 * <p>控制事件不进入普通消息历史和 seq 分配链路，供已读、撤回及瞬时状态使用。
 */
public enum ControlEventTypeEnum {
    /** 已读游标已推进。 */
    READ_ADVANCED(1, "已读游标推进"),
    /** 消息已撤回。 */
    MESSAGE_REVOKED(2, "消息撤回"),
    /** 用户开始输入。 */
    TYPING_STARTED(3, "开始输入"),
    /** 用户停止输入。 */
    TYPING_STOPPED(4, "停止输入"),
    /** 接收设备送达水位已推进。 */
    DELIVERY_ADVANCED(5, "设备送达水位推进");

    private final int code;
    private final String desc;

    ControlEventTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 根据持久化或跨进程编码解析事件类型。 */
    public static ControlEventTypeEnum fromCode(int code) {
        for (ControlEventTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown control event type code: " + code);
    }
}
