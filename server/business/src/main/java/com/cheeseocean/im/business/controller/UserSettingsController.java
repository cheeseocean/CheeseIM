package com.cheeseocean.im.business.controller;

import com.cheeseocean.im.common.core.auth.SessionPrincipal;
import com.cheeseocean.im.business.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.business.model.UpdateSettingsRequest;
import com.cheeseocean.im.business.model.UserSettingsResponse;
import com.cheeseocean.im.business.service.user.UserSettingsServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/im/user/settings")
public class UserSettingsController {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final UserSettingsServiceImpl userSettingsService;

    public UserSettingsController(AccessTokenSessionResolver accessTokenSessionResolver,
                                  UserSettingsServiceImpl userSettingsService) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.userSettingsService = userSettingsService;
    }

    /** 查询当前用户设置 */
    @GetMapping
    public UserSettingsResponse get(@RequestHeader("Authorization") String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        int opt = userSettingsService.getGlobalRecvMsgOpt(session.getUserId());
        return new UserSettingsResponse(opt);
    }

    /** 更新当前用户设置 */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@RequestHeader("Authorization") String authorization,
                       @RequestBody @Valid UpdateSettingsRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        userSettingsService.setGlobalRecvMsgOpt(session.getUserId(), request.getGlobalRecvMsgOpt());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleUnauthorized(IllegalStateException e) {
        return Map.of("code", 40100, "message", e.getMessage());
    }
}
