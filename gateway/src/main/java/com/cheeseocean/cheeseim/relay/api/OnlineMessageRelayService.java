package com.cheeseocean.cheeseim.relay.api;

/**
 * @author xxxcrel
 * Created on 2022/5/19
 */
public interface OnlineMessageRelayService {
    OnlinePushMsgResp onlinePushMsg(OnlinePushMsgReq onlinePushMsgReq);
    GetUsersOnlineStatusResp getUsersOnlineStatus(GetUserOnlineStatusReq getUserOnlineStatusReq);
}
