package com.cheeseocean.im.gateway.relay.impl;

import java.util.Collections;

import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cheeseocean.im.gateway.relay.api.GetUserOnlineStatusReq;
import com.cheeseocean.im.gateway.relay.api.GetUsersOnlineStatusResp;
import com.cheeseocean.im.gateway.relay.api.OnlineMessageRelayService;
import com.cheeseocean.im.gateway.relay.api.OnlinePushMsgReq;
import com.cheeseocean.im.gateway.relay.api.OnlinePushMsgResp;

@DubboService
public class OnlineMessageRelayServiceImpl implements OnlineMessageRelayService {

    private static final Logger logger = LoggerFactory.getLogger(OnlineMessageRelayServiceImpl.class);

    @Override
    public OnlinePushMsgResp onlinePushMsg(OnlinePushMsgReq onlinePushMsgReq) {
        logger.info("onlinePushMsg");
        return OnlinePushMsgResp.newBuilder().resp(Collections.emptyList()).build();
    }

    @Override
    public GetUsersOnlineStatusResp getUsersOnlineStatus(GetUserOnlineStatusReq getUserOnlineStatusReq) {
        logger.info("getUserOnlineStatus");
        return null;
    }
}
