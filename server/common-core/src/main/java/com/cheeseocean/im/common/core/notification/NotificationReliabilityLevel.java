package com.cheeseocean.im.common.core.notification;

/**
 * 通知可靠性等级。
 *
 * <p>用于把通知配置翻译为消息链路中的历史/持久化语义。
 *
 * @author xxxcrel
 */
public enum NotificationReliabilityLevel {
    /**
     * 不持久化，不进入历史。
     */
    UNRELIABLE,
    /**
     * 持久化并进入历史，但不一定作为会话中的最后一条消息展示。
     */
    RELIABLE_NO_MSG,
    /**
     * 持久化并作为普通消息处理。
     */
    RELIABLE_WITH_MSG
}
