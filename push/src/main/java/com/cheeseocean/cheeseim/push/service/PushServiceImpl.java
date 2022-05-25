package com.cheeseocean.cheeseim.push.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import com.cheeseocean.cheeseim.push.api.PushMsgReq;
import com.cheeseocean.cheeseim.push.api.PushMsgResp;
import com.cheeseocean.cheeseim.push.api.PushService;
import com.cheeseocean.cheeseim.relay.api.OnlineMessageRelayService;
import com.cheeseocean.cheeseim.relay.api.OnlinePushMsgReq;
import com.cheeseocean.cheeseim.relay.api.OnlinePushMsgResp;
import com.cheeseocean.cheeseim.relay.api.SingleMsgToUser;

@DubboService
public class PushServiceImpl implements PushService {
    @DubboReference
    private OnlineMessageRelayService onlineRelayService;

    @Override
    public PushMsgResp pushMsg(PushMsgReq pushMsgReq) {
        List<SingleMsgToUser> wsResult = new ArrayList<>();
        OnlinePushMsgResp pushMsgResp = onlineRelayService.onlinePushMsg(OnlinePushMsgReq.newBuilder()
                .operationID(pushMsgReq.getOperationID())
                .msgData(pushMsgReq.getMsgData())
                .pushToUserID(pushMsgReq.getPushToUserID())
                .build());
        return PushMsgResp.newBuilder().resultCode(0).build();
    }
}
