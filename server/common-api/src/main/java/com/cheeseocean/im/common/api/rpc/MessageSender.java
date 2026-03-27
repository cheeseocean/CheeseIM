package com.cheeseocean.im.common.api.rpc;

import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;

public interface MessageSender {

    SendMessageResp sendMessage(SendMessageReq req);
}
