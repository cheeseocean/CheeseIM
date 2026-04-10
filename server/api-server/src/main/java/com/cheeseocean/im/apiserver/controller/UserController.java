package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.facade.UserFacade;
import com.cheeseocean.im.apiserver.model.request.UpdateUserSettingsRequest;
import com.cheeseocean.im.apiserver.model.response.UserSettingsResponse;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/im/user")
public class UserController {

    private final UserFacade userFacade;

    public UserController(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    @GetMapping("/settings")
    public UserSettingsResponse getSettings(SessionPrincipal session) {
        return userFacade.getUserSettings(session);
    }

    @PutMapping("/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSettings(SessionPrincipal session,
                               @RequestBody @Valid UpdateUserSettingsRequest request) {
        userFacade.updateUserSettings(session, request);
    }
}
