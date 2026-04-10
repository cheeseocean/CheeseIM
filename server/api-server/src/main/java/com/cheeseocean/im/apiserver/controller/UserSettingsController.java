package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.business.model.UpdateSettingsRequest;
import com.cheeseocean.im.business.model.UserSettingsResponse;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.user.UserInfoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/im/user/settings")
public class UserSettingsController {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final UserInfoService userInfoService;

    public UserSettingsController(AccessTokenSessionResolver accessTokenSessionResolver,
                                  UserInfoService userInfoService) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.userInfoService = userInfoService;
    }

    @GetMapping
    public UserSettingsResponse get(@RequestHeader("Authorization") String authorization) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        int opt = userInfoService.getReceiveOptions(session.getUserId());
        return new UserSettingsResponse(opt);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@RequestHeader("Authorization") String authorization,
                       @RequestBody @Valid UpdateSettingsRequest request) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
//        userInfoService.setReceiveOptions(session.getUserId(), request.getGlobalRecvMsgOpt());
    }
}
