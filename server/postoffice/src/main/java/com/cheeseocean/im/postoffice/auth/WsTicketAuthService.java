package com.cheeseocean.im.postoffice.auth;

import com.cheeseocean.im.common.model.auth.SessionPrincipal;

public interface WsTicketAuthService {

    SessionPrincipal authenticate(String ticket);
}
