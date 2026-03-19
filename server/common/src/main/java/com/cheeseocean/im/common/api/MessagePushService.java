package com.cheeseocean.im.common.api;

import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.dto.PushResult;

public interface MessagePushService {

    PushResult pushOffline(String userId, MessageProto message);

    void cancelPending(String serverMsgId, String userId);
}
