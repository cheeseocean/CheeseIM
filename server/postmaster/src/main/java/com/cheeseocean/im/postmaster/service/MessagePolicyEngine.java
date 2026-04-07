package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.Message;

public interface MessagePolicyEngine {

    MessageRouteDecision decide(Message event);
}
