package com.cheeseocean.im.client.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WsTicketResponse(
        String ticket,
        @JsonProperty("expire_at") long expireAt,
        @JsonProperty("ws_url") String wsUrl
) {
}
