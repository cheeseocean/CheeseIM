package com.cheeseocean.im.common.api.rpc;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;

public interface OnlineDispatcher {

    DispatchMessageResp dispatchMessage(DispatchMessageReq req);
}
