package com.cheeseocean.im.common.entity;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户发送消息返回
 *
 * @author xxxcrel
 * @date 2023/05/16
 */
@Data
@Builder
public class CheeseResponse implements Serializable {
    int type;

    int msgIncr;

    int errCode;

    String errMsg;

    byte[] data;

}
