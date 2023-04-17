package com.cheeseocean.im.msg.api;

import org.apache.dubbo.config.annotation.DubboService;

/**
 * @author xxxcrel
 * @date 2023/4/13 12:00
 */
public interface ChatService {

    /**
     * C2C发送消息
     * @param sendMsgReq 发送消息请求
     * @return SendMsgResp 消息返回信息
     */
    SendMsgResp sendMessage(SendMsgReq sendMsgReq);
}
