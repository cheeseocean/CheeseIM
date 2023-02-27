package com.cheeseocean.im.gateway.connection;

import io.netty.channel.Channel;

import java.util.Map;

public class IMConnection {

    private Map<Integer/*platformID*/, Channel/*Connection*/> channelMap;

    public IMConnection() {

    }

    public void addUserConn(Integer platformID, Channel channel) {
        channelMap.put(platformID, channel);
    }

}
