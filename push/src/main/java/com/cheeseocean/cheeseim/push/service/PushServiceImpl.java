package com.cheeseocean.cheeseim.push.service;

import org.apache.dubbo.config.annotation.DubboService;

import com.cheeseocean.cheeseim.push.api.PushMsgReq;
import com.cheeseocean.cheeseim.push.api.PushMsgResp;
import com.cheeseocean.cheeseim.push.api.PushService;

@DubboService
public class PushServiceImpl implements PushService {
    @Override
    public PushMsgResp pushMsg(PushMsgReq pushMsgReq) {
        System.out.println("hello");
        return PushMsgResp.newBuilder().resultCode(0).build();
    }
}
