package com.cheeseocean.im.client.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class AuthHttpClient {

    private final String baseUrl;
    private final HttpExchange httpExchange;
    private final ObjectMapper objectMapper;

    public AuthHttpClient(String baseUrl) {
        this(baseUrl, new JdkHttpExchange(HttpClient.newHttpClient()), new ObjectMapper());
    }

    public AuthHttpClient(String baseUrl, HttpExchange httpExchange) {
        this(baseUrl, httpExchange, new ObjectMapper());
    }

    AuthHttpClient(String baseUrl, HttpExchange httpExchange, ObjectMapper objectMapper) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.httpExchange = httpExchange;
        this.objectMapper = objectMapper;
    }

    public AuthLoginResponse login(AuthLoginRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(new WireLoginRequest(
                    request.userId(),
                    request.platformId(),
                    request.deviceId(),
                    request.clientVersion()
            ));
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            RawHttpResponse response = httpExchange.execute(httpRequest);
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("auth login failed: http " + response.statusCode());
            }

            AuthLoginResponse loginResponse = objectMapper.readValue(response.body(), AuthLoginResponse.class);
            if (loginResponse.accessToken() == null || loginResponse.accessToken().isBlank()) {
                throw new IllegalStateException("access token missing in auth response");
            }
            return loginResponse;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to call auth login", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("auth login interrupted", e);
        }
    }

    public WsTicketResponse issueWsTicket(String accessToken) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/im/ws-ticket"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                    .build();

            RawHttpResponse response = httpExchange.execute(httpRequest);
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("ws ticket failed: http " + response.statusCode());
            }

            WsTicketResponse wsTicketResponse = objectMapper.readValue(response.body(), WsTicketResponse.class);
            if (wsTicketResponse.ticket() == null || wsTicketResponse.ticket().isBlank()) {
                throw new IllegalStateException("ticket missing in ws ticket response");
            }
            return wsTicketResponse;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to issue ws ticket", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ws ticket interrupted", e);
        }
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    public interface HttpExchange {
        RawHttpResponse execute(HttpRequest request) throws IOException, InterruptedException;
    }

    public record RawHttpResponse(int statusCode, String body) {
    }

    private record WireLoginRequest(
            String userId,
            Integer platformId,
            String deviceId,
            String clientVersion
    ) {
    }

    private static final class JdkHttpExchange implements HttpExchange {

        private final HttpClient httpClient;

        private JdkHttpExchange(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public RawHttpResponse execute(HttpRequest request) throws IOException, InterruptedException {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new RawHttpResponse(response.statusCode(), response.body());
        }
    }
}
