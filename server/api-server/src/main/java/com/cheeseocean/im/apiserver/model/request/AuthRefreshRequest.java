package com.cheeseocean.im.apiserver.model.request;

/** HTTP token 刷新请求。 */
public class AuthRefreshRequest {
    private String refreshToken;
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
