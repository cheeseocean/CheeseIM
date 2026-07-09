package com.cheeseocean.im.common.api.permission;

import lombok.Data;

import java.io.Serializable;

/**
 * 消息发送权限聚合查询请求。
 *
 * <p>发送热路径只应发起一次远程权限查询，避免黑名单、用户接收选项、
 * 会话接收选项分别 Dubbo 调用造成 RTT 放大。
 */
@Data
public class MessageSendPermissionRequest implements Serializable {

    private String senderId;
    private String receiverId;
    private String conversationId;
}
