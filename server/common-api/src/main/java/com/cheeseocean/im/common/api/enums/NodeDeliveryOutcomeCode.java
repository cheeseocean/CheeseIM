package com.cheeseocean.im.common.api.enums;

/**
 * postoffice 节点在线投递的最终结果。
 *
 * <p>该结果会经 delivery-outcome 队列跨进程传递，code 必须保持稳定。</p>
 */
public enum NodeDeliveryOutcomeCode {

    DELIVERED(1, "至少一个目标连接已写入"),
    NO_ACTIVE_CONNECTION(2, "节点上已无目标连接"),
    FAILED_FINAL(3, "节点投递重试耗尽");

    private final int code;
    private final String desc;

    NodeDeliveryOutcomeCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static NodeDeliveryOutcomeCode fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (NodeDeliveryOutcomeCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
