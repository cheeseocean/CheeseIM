package com.cheeseocean.im.common.api;

import com.cheeseocean.im.common.dto.GatewayPushResult;
import com.cheeseocean.im.common.dto.MessageProto;

public interface GatewayPushService {

    GatewayPushResult pushToUser(String receiverId, MessageProto message);
}
