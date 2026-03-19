package com.cheeseocean.im.postbox.policy;

import org.springframework.stereotype.Component;

@Component
public class ChannelPolicy {

    public boolean canAccess(String conversationId, String userId) {
        return false;
    }
}
