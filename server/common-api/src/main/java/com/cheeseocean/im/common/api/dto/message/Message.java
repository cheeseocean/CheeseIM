package com.cheeseocean.im.common.api.dto.message;

import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessageSource;
import com.cheeseocean.im.common.api.enums.MessageStatus;
import com.cheeseocean.im.common.api.enums.PlatformType;
import com.cheeseocean.im.common.api.enums.SessionType;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 消息流转中间模型。
 *
 * <p>用于承载消息在网关、投递、存储等链路中的通用字段，
 * 兼容单聊、群聊和通知三类会话场景。
 */
@Data
public class Message implements Serializable {

    private static final long                serialVersionUID = 1L;
    /**
     * 客户端侧消息 ID，用于幂等去重和请求响应对齐。
     */
    private              String              clientMsgId;
    /**
     * 服务端生成的消息 ID，作为全链路唯一标识。
     */
    private              String              serverMsgId;
    /**
     * 发送者用户 ID。
     */
    private              String              senderId;
    /**
     * 发送者昵称
     */
    private              String              senderNickName;
    /**
     * 发送者头像
     */
    private              String              senderAvatar;
    /**
     * 接收者用户 ID，单聊/通知场景使用。
     */
    private              String              receiverId;
    /**
     * 群组 ID，群聊场景使用。
     */
    private              String              groupId;
    /**
     * 消息正文内容。
     */
    private              byte[]              content;
    /**
     * 消息内容类型，取值见 {@link com.cheeseocean.im.common.api.enums.ContentType}。
     */
    private              ContentType         contentType;
    /**
     * 会话类型，取值见 {@link com.cheeseocean.im.common.api.enums.SessionType}。
     */
    private              SessionType         sessionType;
    /**
     * 客户端声明的发送时间。
     */
    private              Long                sendTime;
    /**
     * 服务端创建时间。
     */
    private              Long                createTime;
    /**
     * 消息状态，取值见 {@link com.cheeseocean.im.common.api.enums.MessageStatus}。
     */
    private              MessageStatus       status;
    /**
     * 发送端平台标识，取值见 {@link com.cheeseocean.im.common.api.enums.PlatformType}。
     */
    private              PlatformType        platformType;
    /**
     * 附加信息字段，通常用于补充会话或消息元数据。
     */
    private              Map<String, String> attributes;
    /**
     * 业务唯一标识，供外部系统幂等或追踪使用。
     */
    private              String              uniqueId;
    /**
     * 消息来源标识，取值见 {@link com.cheeseocean.im.common.api.enums.MessageSource}。
     */
    private              MessageSource       source;
    /**
     * 消息选项集合，例如是否计入会话、未读或离线推送。
     */
    private              MessageOptions      options;
    //-----------------服务端填充---------------------
    /**
     * 消息序列号，会话内严格递增、唯一
     */
    private              Long                seq;
}
