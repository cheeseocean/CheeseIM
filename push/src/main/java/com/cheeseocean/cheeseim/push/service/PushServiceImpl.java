package com.cheeseocean.cheeseim.push.service;

import com.cheeseocean.cheeseim.push.api.PushMsgReq;
import com.cheeseocean.cheeseim.push.api.PushMsgResp;
import com.cheeseocean.cheeseim.push.api.PushService;

public class PushServiceImpl implements PushService {

    @Override
    public PushMsgResp pushMsg(PushMsgReq pushMsgReq) {
        return PushMsgResp.newBuilder().resultCode(0).build();
    }
}
