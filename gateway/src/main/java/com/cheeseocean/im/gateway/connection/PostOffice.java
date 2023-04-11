package com.cheeseocean.im.gateway.connection;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
public class PostOffice {

    private Map<String, UserConn> connectionMap;

    private ReadWriteLock rwLock;

    public PostOffice() {
        rwLock = new ReentrantReadWriteLock();
    }


    public void addUserConn(Channel channel, String uid, int platformID, String token, String opID) {

    }

}
