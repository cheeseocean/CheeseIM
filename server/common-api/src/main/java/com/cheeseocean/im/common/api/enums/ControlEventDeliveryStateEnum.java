package com.cheeseocean.im.common.api.enums;

/**
 * 会话控制事件 outbox 投递状态。
 */
public enum ControlEventDeliveryStateEnum {
    /** 等待投递。 */
    PENDING(1, "待投递"),
    /** 已被某个投递器租约领取。 */
    CLAIMED(2, "投递中"),
    /** 已完成在线控制通知投递。 */
    DELIVERED(3, "已投递");

    private final int code;
    private final String desc;

    ControlEventDeliveryStateEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 根据持久化编码解析投递状态。 */
    public static ControlEventDeliveryStateEnum fromCode(int code) {
        for (ControlEventDeliveryStateEnum state : values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown control event delivery state code: " + code);
    }
}
