package com.cheeseocean.im.common.api.rpc;

import com.cheeseocean.im.common.api.dto.push.OfflinePushReq;
import com.cheeseocean.im.common.api.dto.push.PushResult;

public interface OfflinePushRpc {

    PushResult pushOffline(OfflinePushReq req);

    void cancelPending(String serverMsgId, String userId);
}
