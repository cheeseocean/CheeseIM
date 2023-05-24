package com.cheeseocean.im.postoffice.connection;

import io.netty.channel.Channel;

import java.util.Map;

/**
 * @author xxxcrel
 * Created on 2023/3/7
 */
public class UserConn {

    private Map<Integer/*platformID*/, Channel/*Connection*/> channelMap;

    public UserConn() {

    }

    public void addUserConn(Integer platformID, Channel channel) {
        channelMap.put(platformID, channel);
    }

}
