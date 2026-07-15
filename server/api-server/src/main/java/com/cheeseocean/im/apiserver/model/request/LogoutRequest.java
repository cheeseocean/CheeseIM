package com.cheeseocean.im.apiserver.model.request;

/** HTTP 登出请求。 */
public class LogoutRequest {
    private String sessionId;
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
