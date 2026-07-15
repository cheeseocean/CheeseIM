package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.authcenter.model.AuthLoginRequest;
import com.cheeseocean.im.authcenter.model.AuthRefreshRequest;
import com.cheeseocean.im.authcenter.model.AuthResponse;
import com.cheeseocean.im.authcenter.model.KickoffDeviceRequest;
import com.cheeseocean.im.authcenter.model.LogoutRequest;
import com.cheeseocean.im.authcenter.session.SessionLifecycleService;
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

    private final SessionLifecycleService sessionLifecycleService;

    public AuthController(SessionLifecycleService sessionLifecycleService) {
        this.sessionLifecycleService = sessionLifecycleService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthLoginRequest request) {
        return sessionLifecycleService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody AuthRefreshRequest request) {
        return sessionLifecycleService.refresh(request);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody LogoutRequest request) {
        sessionLifecycleService.logout(request.getSessionId());
        return Map.of("success", true);
    }

    @PostMapping("/devices/{deviceId}/kickoff")
    public Map<String, Object> kickoffDevice(@PathVariable String deviceId,
                                             @RequestBody KickoffDeviceRequest request) {
        sessionLifecycleService.kickoffDevice(request.getUserId(), deviceId);
        return Map.of("success", true);
    }

    @PostMapping("/kickoff-all/{userId}")
    public Map<String, Object> kickoffAll(@PathVariable String userId) {
        sessionLifecycleService.kickoffAll(userId);
        return Map.of("success", true);
    }
}
