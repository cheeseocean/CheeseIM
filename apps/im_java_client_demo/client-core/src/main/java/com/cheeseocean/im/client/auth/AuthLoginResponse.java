package com.cheeseocean.im.client.auth;

public record AuthLoginResponse(
        String userId,
        String sessionId,
        String accessToken,
        String refreshToken,
        Long accessExpireAt,
        Long refreshExpireAt
) {
}
