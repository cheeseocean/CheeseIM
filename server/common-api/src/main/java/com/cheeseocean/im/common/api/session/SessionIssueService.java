package com.cheeseocean.im.common.api.session;

import com.cheeseocean.im.common.core.auth.WsTicketPrincipal;

public interface SessionIssueService {

    WsTicketPrincipal issueWsTicket(String accessToken, String deviceId, String platform, String clientVersion);

    WsTicketPrincipal consumeWsTicket(String ticket);
}
