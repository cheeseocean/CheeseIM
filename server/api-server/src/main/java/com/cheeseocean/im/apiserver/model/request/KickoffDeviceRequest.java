package com.cheeseocean.im.apiserver.model.request;

/** HTTP 踢下线请求。 */
public class KickoffDeviceRequest {
    private String userId;
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
