package com.cheeseocean.im.postoffice.connection;

import io.netty.channel.Channel;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author xxxcrel
 * Created on 2022/5/22
 */
@Component
public class ConnectionHolder {

    private Map<String, UserConn> connectionMap;

    private ReadWriteLock rwLock;

    public ConnectionHolder() {
        rwLock = new ReentrantReadWriteLock();
    }


    public void addUserConn(Channel channel, String uid, int platformID, String token, String opID) {

    }

}
