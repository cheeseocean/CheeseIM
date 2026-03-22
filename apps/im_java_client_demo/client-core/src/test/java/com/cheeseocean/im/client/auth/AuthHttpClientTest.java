package com.cheeseocean.im.client.auth;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthHttpClientTest {

    @Test
    void loginShouldPostUsernamePasswordAndParseAccessToken() throws Exception {
        AuthHttpClient client = new AuthHttpClient(
                "http://127.0.0.1:8080",
                request -> {
                    assertEquals("http://127.0.0.1:8080/api/auth/login", request.uri().toString());
                    assertEquals("POST", request.method());
                    return new AuthHttpClient.RawHttpResponse(200, """
                            {"userId":"userA","sessionId":"sess:1","accessToken":"access-token","refreshToken":"refresh-token","accessExpireAt":1710000000000,"refreshExpireAt":1710000100000}
                            """);
                }
        );

        AuthLoginResponse response = client.login(new AuthLoginRequest("userA", "secret", 2, "device-a", "1.0.0"));

        assertEquals("userA", response.userId());
        assertEquals("sess:1", response.sessionId());
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals(1710000000000L, response.accessExpireAt());
        assertEquals(1710000100000L, response.refreshExpireAt());
    }

    @Test
    void loginShouldFailWhenAccessTokenMissing() {
        AuthHttpClient client = new AuthHttpClient(
                "http://127.0.0.1:8080",
                request -> new AuthHttpClient.RawHttpResponse(200, """
                        {"userId":"userA","sessionId":"sess:1"}
                        """)
        );

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.login(new AuthLoginRequest("userA", "secret", 2, "device-a", "1.0.0")));

        assertEquals("access token missing in auth response", error.getMessage());
    }
}
