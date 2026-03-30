package com.cheeseocean.im.postoffice.server;

import lombok.Data;

/**
 * 服务器状态类
 *
 * @author xxxcrel
 */
@Data
public class ServerStatus {
    private boolean running;
    private int     port;
    private String  protocol;
    private String  websocketPath;
    private boolean sslEnabled;
    private int     bossThreads;
    private int     workerThreads;
    private long    startTime;
    private long    totalConnections;
    private long    onlineUsers;
}
