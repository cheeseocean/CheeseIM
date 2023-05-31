package com.cheeseocean.im.message.api;

import lombok.Data;

import java.io.Serializable;

/**
 * @author xxxcrel
 * @date 2023/4/17 10:30
 */
@Data
public class SendMessageReq implements Serializable {

    private byte[] payload;

}
