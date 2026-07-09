package com.cheeseocean.im.common.api.permission;

/**
 * 消息发送权限聚合服务。
 *
 * <p>由 business 提供，postbox 在发送热路径一次性获取黑名单、
 * 用户级接收选项与会话级接收选项。
 */
public interface MessageSendPermissionService {

    MessageSendPermissionResult check(MessageSendPermissionRequest request);
}
