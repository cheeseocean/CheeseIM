package com.cheeseocean.im.common.api.enums;

/**
 * 消息投递过程状态枚举。
 *
 * @author xxxcrel
 */
public enum DeliveryState implements IEnum {
    /** 初始状态。 */
    INIT("初始化"),
    /** 已完成持久化。 */
    PERSISTED("已持久化"),
    /** 已完成路由。 */
    ROUTED("已路由"),
    /** 正在在线投递。 */
    ONLINE_DELIVERING("在线投递中"),
    /** 在线投递已确认。 */
    ONLINE_CONFIRMED("在线投递已确认"),
    /** 已进入收件箱。 */
    INBOXED("已入箱"),
    /** 已触发推送。 */
    PUSH_TRIGGERED("已触发推送"),
    /** 已读。 */
    READ("已读"),
    /** 已撤回。 */
    RECALLED("已撤回"),
    /** 可恢复失败。 */
    FAILED_RECOVERABLE("可恢复失败"),
    /** 最终失败。 */
    FAILED_FINAL("最终失败");

    private final String desc;

    DeliveryState(String desc) {
        this.desc = desc;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
