package com.cheeseocean.im.common.entity;

import lombok.Data;

import java.util.Map;

/**
 * 消息体
 * @author xxxcrel
 * Created on 2022/5/20
 */
@Data
public class CheeseMessage {

    /**
     * 发送方ID
     */
    private String sid;

    /**
     * 接收方ID
     */
    private String rid;

    /**
     * 群ID
     */
    private String gid;

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
     * 消息内容
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
    private Map<String, Integer> options;

}
