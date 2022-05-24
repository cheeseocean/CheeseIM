package com.cheeseocean.cheeseim.push.service.impl;

import com.cheeseocean.cheeseim.push.service.PushMsgReq;
import com.cheeseocean.cheeseim.push.service.PushMsgResp;
import com.cheeseocean.cheeseim.push.service.PushService;

public class PushServiceImpl implements PushService {

    @Override
    public PushMsgResp pushMsg(PushMsgReq pushMsgReq) {
        return PushMsgResp.newBuilder().resultCode(0).build();
    }
}
