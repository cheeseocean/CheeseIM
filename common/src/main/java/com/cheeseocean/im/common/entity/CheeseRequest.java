package com.cheeseocean.im.common.entity;

import lombok.Data;

@Data
public class CheeseRequest {

    /**
     * 请求类型
     */
    int type;

    int msgIncr;

    /**
     * 用户ID
     */
    String uid;

    /**
     * 消息负载
     */
    byte[] payload;

}
