package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.common.api.dto.user.WsTicketPrincipal;
import com.cheeseocean.im.common.api.session.SessionIssueService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 长连接票据签发入口。
 */
@RestController
@RequestMapping("/api/im")
public class WsTicketController {

    private final SessionIssueService sessionIssueService;

    public WsTicketController(SessionIssueService sessionIssueService) {
        this.sessionIssueService = sessionIssueService;
    }

    @PostMapping("/ws-ticket")
    public Map<String, Object> issue(@RequestHeader("Authorization") String authorization,
                                     @RequestBody(required = false) Map<String, Object> body) {
        String accessToken = extractBearerToken(authorization);
        String deviceId = body == null ? null : stringValue(body.get("device_id"));
        String platform = body == null ? null : stringValue(body.get("platform"));
        String clientVersion = body == null ? null : stringValue(body.get("client_version"));
        WsTicketPrincipal ticket = sessionIssueService.issueWsTicket(accessToken, deviceId, platform, clientVersion);
        return Map.of(
                "ticket", ticket.getTicket(),
                "expire_at", ticket.getExpireAt(),
                "ws_url", "/ws"
        );
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new IllegalStateException("access token missing");
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
