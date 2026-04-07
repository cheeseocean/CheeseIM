package com.cheeseocean.im.postoffice.auth;

import com.cheeseocean.im.common.api.session.SessionPrincipal;

public interface WsTicketAuthService {

    SessionPrincipal authenticate(String ticket);
}
