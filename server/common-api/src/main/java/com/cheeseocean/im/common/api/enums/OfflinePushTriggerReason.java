package com.cheeseocean.im.common.api.enums;

/**
 * 离线推送触发原因。
 *
 * <p>code 写入 {@code OfflinePushEvent.attributes}，用于区分普通离线检查和节点失败补偿。</p>
 */
public enum OfflinePushTriggerReason {

    ROUTE_ABSENT("route_absent", "路由为空"),
    NODE_DELIVERY_FAILED("node_delivery_failed", "所有目标节点投递失败"),
    NODE_DELIVERY_TIMEOUT("node_delivery_timeout", "节点投递超过最终期限");

    private final String code;
    private final String desc;

    OfflinePushTriggerReason(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static OfflinePushTriggerReason fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OfflinePushTriggerReason value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
