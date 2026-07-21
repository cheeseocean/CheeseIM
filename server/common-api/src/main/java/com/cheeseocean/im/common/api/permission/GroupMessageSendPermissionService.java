package com.cheeseocean.im.common.api.permission;

/**
 * 群消息发送权限聚合服务。
 *
 * <p>一次返回群状态、扩散类型以及批内所有发送者的成员/禁言判断。</p>
 */
public interface GroupMessageSendPermissionService {

    /**
     * 批量检查同一群内的发送者；provider 必须为每个有效 senderId 返回一个 decision。
     */
    GroupMessageSendPermissionResult check(GroupMessageSendPermissionRequest request);
}
