package com.cheeseocean.im.client.session;

public class ClientSession {

    private String userId;
    private Integer platformId;
    private String accessToken;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private String latestServerMsgId;
    private String latestClientMsgId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Integer platformId) {
        this.platformId = platformId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public void setConnectionState(ConnectionState connectionState) {
        this.connectionState = connectionState;
    }

    public boolean isConnected() {
        return connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.AUTHENTICATED;
    }

    public boolean isAuthenticated() {
        return connectionState == ConnectionState.AUTHENTICATED;
    }

    public String getLatestServerMsgId() {
        return latestServerMsgId;
    }

    public void setLatestServerMsgId(String latestServerMsgId) {
        this.latestServerMsgId = latestServerMsgId;
    }

    public String getLatestClientMsgId() {
        return latestClientMsgId;
    }

    public void setLatestClientMsgId(String latestClientMsgId) {
        this.latestClientMsgId = latestClientMsgId;
    }
}
