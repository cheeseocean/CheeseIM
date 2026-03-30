package com.cheeseocean.im.postoffice.server;

/**
 * @author xxxcrel
 * @date 2026/3/30 20:36
 */
public interface Server {

    void start() throws Exception;

    void shutdown();

    ServerStatus getStatus();
}
