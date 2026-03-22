package com.cheeseocean.im.postoffice.auth;

import com.cheeseocean.im.common.core.auth.SessionPrincipal;

public interface WsTicketAuthService {

    SessionPrincipal authenticate(String ticket);
}
