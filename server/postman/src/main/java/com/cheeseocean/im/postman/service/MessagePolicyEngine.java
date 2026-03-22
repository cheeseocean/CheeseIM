package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.event.IngressEvent;

public interface MessagePolicyEngine {

    MessageRouteDecision decide(IngressEvent event);
}
