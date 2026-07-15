package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.model.request.AuthLoginRequest;
import com.cheeseocean.im.apiserver.model.request.AuthRefreshRequest;
import com.cheeseocean.im.apiserver.model.request.KickoffDeviceRequest;
import com.cheeseocean.im.apiserver.model.request.LogoutRequest;
import com.cheeseocean.im.common.api.auth.AuthenticationCommand;
import com.cheeseocean.im.common.api.auth.AuthenticationResult;
import com.cheeseocean.im.common.api.auth.AuthenticationService;
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
    public AuthenticationResult login(@RequestBody AuthLoginRequest request) {
        AuthenticationCommand command = new AuthenticationCommand();
        command.setUserId(request.getUserId());
        command.setPlatformId(request.getPlatformId());
        command.setDeviceId(request.getDeviceId());
        command.setClientVersion(request.getClientVersion());
        return authenticationService.login(command);
    }

    @PostMapping("/refresh")
    public AuthenticationResult refresh(@RequestBody AuthRefreshRequest request) {
        return authenticationService.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody LogoutRequest request) {
        authenticationService.logout(request.getSessionId());
        return Map.of("success", true);
    }

    @PostMapping("/devices/{deviceId}/kickoff")
    public Map<String, Object> kickoffDevice(@PathVariable String deviceId,
                                             @RequestBody KickoffDeviceRequest request) {
        authenticationService.kickoffDevice(request.getUserId(), deviceId);
        return Map.of("success", true);
    }

    @PostMapping("/kickoff-all/{userId}")
    public Map<String, Object> kickoffAll(@PathVariable String userId) {
        authenticationService.kickoffAll(userId);
        return Map.of("success", true);
    }
}
