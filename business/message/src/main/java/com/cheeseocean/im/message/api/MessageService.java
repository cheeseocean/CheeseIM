package com.cheeseocean.im.message.api;

/**
 * @author xxxcrel
 * @date 2023/4/13 12:00
 */
public interface MessageService {

    /**
     * C2C发送消息
     * @param sendMessageReq 发送消息请求
     * @return SendMsgResp 消息返回信息
     */
    SendMessageResp sendMessage(SendMessageReq sendMessageReq);
}
