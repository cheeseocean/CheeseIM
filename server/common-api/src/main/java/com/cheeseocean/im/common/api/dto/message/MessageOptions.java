package com.cheeseocean.im.common.api.dto.message;

import lombok.Data;

import java.io.Serializable;

/**
 * 与 Protobuf 一一对应的消息选项，保留全部字段以兼容已有载荷和指纹。
 * 保留字段可序列化，但不表示服务端已实现对应策略。
 */
@Data
public class MessageOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 控制 ingress 是否持久化并分配 seq。 */
    private Boolean needHistory;
    /** 兼容保留；当前不能独立控制会话更新。 */
    private Boolean needConversation;
    /** 兼容保留；当前不能独立控制未读更新。 */
    private Boolean needUnreadCount;
    /** 控制 ingress 是否发布投递事件。 */
    private Boolean needOnlinePush;
    /** 由 postman 读取，控制离线推送资格。 */
    private Boolean needOfflinePush;
    /** 兼容保留；发送者其他设备实时同步尚未实现。 */
    private Boolean senderSync;
    /** 控制 ingress 通知会话分流。 */
    private Boolean notification;
    /** 兼容保留；当前不能独立控制最后消息更新。 */
    private Boolean needLastMessage;

}
