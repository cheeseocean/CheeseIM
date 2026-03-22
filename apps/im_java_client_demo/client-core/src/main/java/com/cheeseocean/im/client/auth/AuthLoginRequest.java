package com.cheeseocean.im.client.auth;

public record AuthLoginRequest(
        String userId,
        String password,
        Integer platformId,
        String deviceId,
        String clientVersion
) {
}
