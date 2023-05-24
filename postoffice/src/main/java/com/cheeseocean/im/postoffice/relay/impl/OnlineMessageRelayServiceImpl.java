package com.cheeseocean.im.postoffice.relay.impl;

import java.util.Collections;

import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cheeseocean.im.postoffice.relay.api.GetUserOnlineStatusReq;
import com.cheeseocean.im.postoffice.relay.api.GetUsersOnlineStatusResp;
import com.cheeseocean.im.postoffice.relay.api.OnlineMessageRelayService;
import com.cheeseocean.im.postoffice.relay.api.OnlinePushMsgReq;
import com.cheeseocean.im.postoffice.relay.api.OnlinePushMsgResp;

//@DubboService
public class OnlineMessageRelayServiceImpl implements OnlineMessageRelayService{

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
