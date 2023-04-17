package com.cheeseocean.im.msg.api;

import lombok.Data;

import java.util.Map;

/**
 * @author xxxcrel
 * @date 2023/4/17 10:30
 */
@Data
public class SendMsgReq {

    /**
     * 发送方ID
     */
    private String senderID;

    /**
     * 接收方ID
     */
    private String receiverID;

    /**
     * 群ID
     */
    private String groupID;

    /**
     * 平台代码
     */
    private int platform;

    /**
     * 发送方昵称
     */
    private String nickname;

    /**
     * 发送方头像
     */
    private String avatar;

    /**
     * 消息类型(0:单聊, 1:群聊)
     */
    private int type;

    /**
     * 内容类型
     */
    private int contentType;

    /**
     * 消息负载
     */
    private byte[] content;

    /**
     * 消息发送时间
     */
    private long sendTime;

    /**
     * 消息创建时间
     */
    private long createTime;

    /**
     * 消息状态
     */
    private int status;

    /**
     * 配置开关
     */
    private Map<String, Boolean> options;
}
