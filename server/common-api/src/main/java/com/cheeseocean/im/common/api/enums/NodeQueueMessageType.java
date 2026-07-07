package com.cheeseocean.im.common.api.enums;

/**
 * 节点队列消息类型。
 *
 * <p>同一个 postoffice 节点队列既承载在线消息投递，也承载必须命中目标网关节点的控制命令。
 * 用显式类型码区分 payload，避免消费者靠 JSON 字段猜测语义。
 */
public enum NodeQueueMessageType {

    /**
     * 在线消息投递，payload 为 DispatchMessageReq JSON。
     */
    DELIVERY(1, "在线消息投递"),

    /**
     * 踢下线控制命令，payload 为 KickoffCommand JSON。
     */
    KICKOFF(2, "踢下线命令");

    private final int code;
    private final String desc;

    NodeQueueMessageType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static NodeQueueMessageType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (NodeQueueMessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
