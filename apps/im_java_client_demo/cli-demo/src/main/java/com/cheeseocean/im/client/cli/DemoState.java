package com.cheeseocean.im.client.cli;

import com.cheeseocean.im.client.auth.AuthLoginResponse;
import com.cheeseocean.im.client.session.ClientSession;
import com.cheeseocean.im.client.tcp.TcpImClient;

public class DemoState {

    private final ClientSession session = new ClientSession();
    private AuthLoginResponse loginResponse;
    private TcpImClient tcpImClient;

    public ClientSession session() {
        return session;
    }

    public AuthLoginResponse loginResponse() {
        return loginResponse;
    }

    public void setLoginResponse(AuthLoginResponse loginResponse) {
        this.loginResponse = loginResponse;
    }

    public TcpImClient tcpImClient() {
        return tcpImClient;
    }

    public void setTcpImClient(TcpImClient tcpImClient) {
        this.tcpImClient = tcpImClient;
    }
}
