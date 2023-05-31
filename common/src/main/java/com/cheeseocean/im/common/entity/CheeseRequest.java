package com.cheeseocean.im.common.entity;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户发送消息请求
 *
 * @author xxxcrel
 * @date 2023/05/24
 */
@Data
@Builder
public class CheeseRequest implements Serializable {

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
