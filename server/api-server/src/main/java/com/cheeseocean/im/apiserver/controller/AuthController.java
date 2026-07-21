package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.PublicApi;
import com.cheeseocean.im.apiserver.exception.ApiAuthorizationException;
import com.cheeseocean.im.apiserver.model.request.AuthLoginRequest;
import com.cheeseocean.im.apiserver.model.request.AuthRefreshRequest;
import com.cheeseocean.im.apiserver.model.request.KickoffDeviceRequest;
import com.cheeseocean.im.apiserver.model.request.LogoutRequest;
import com.cheeseocean.im.common.api.auth.AuthenticationCommand;
import com.cheeseocean.im.common.api.auth.AuthenticationResult;
import com.cheeseocean.im.common.api.auth.AuthenticationService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证相关的 HTTP 入口，仅负责请求转发与状态码封装。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    @PublicApi
    public AuthenticationResult login(@RequestBody AuthLoginRequest request) {
        AuthenticationCommand command = new AuthenticationCommand();
        command.setUserId(request.getUserId());
        command.setIdentityAssertion(request.getIdentityAssertion());
        command.setPlatformId(request.getPlatformId());
        command.setDeviceId(request.getDeviceId());
        command.setClientVersion(request.getClientVersion());
        return authenticationService.login(command);
    }

    @PostMapping("/refresh")
    @PublicApi
    public AuthenticationResult refresh(@RequestBody AuthRefreshRequest request) {
        return authenticationService.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(SessionPrincipal session,
                                      @RequestBody(required = false) LogoutRequest request) {
        if (request != null && request.getSessionId() != null) {
            requireOwned(request.getSessionId(), session.getSessionId(), "session");
        }
        authenticationService.logout(session.getSessionId());
        return Map.of("success", true);
    }

    @PostMapping("/devices/{deviceId}/kickoff")
    public Map<String, Object> kickoffDevice(SessionPrincipal session,
                                             @PathVariable String deviceId,
                                             @RequestBody(required = false) KickoffDeviceRequest request) {
        if (request != null && request.getUserId() != null) {
            requireOwned(request.getUserId(), session.getUserId(), "user");
        }
        authenticationService.kickoffDevice(session.getUserId(), deviceId);
        return Map.of("success", true);
    }

    @PostMapping("/kickoff-all/{userId}")
    public Map<String, Object> kickoffAll(SessionPrincipal session,
                                         @PathVariable String userId) {
        requireOwned(userId, session.getUserId(), "user");
        authenticationService.kickoffAll(session.getUserId());
        return Map.of("success", true);
    }

    private void requireOwned(String requestedId, String principalId, String resourceType) {
        if (requestedId == null || !requestedId.equals(principalId)) {
            throw new ApiAuthorizationException(resourceType + " ownership mismatch");
        }
    }
}
