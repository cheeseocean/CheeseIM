package com.cheeseocean.im.apiserver.facade;

import com.cheeseocean.im.apiserver.model.request.UpdateUserSettingsRequest;
import com.cheeseocean.im.apiserver.model.response.UserSettingsResponse;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.user.UserInfoService;
import org.springframework.stereotype.Service;

@Service
public class UserFacade {

    private final UserInfoService userInfoService;

    public UserFacade(UserInfoService userInfoService) {
        this.userInfoService = userInfoService;
    }

    public UserSettingsResponse getUserSettings(SessionPrincipal session) {
        UserSettingsResponse response = new UserSettingsResponse();
        response.setReceiveOpt(userInfoService.getReceiveOptions(session.getUserId()));
        return response;
    }

    public void updateUserSettings(SessionPrincipal session, UpdateUserSettingsRequest request) {
        // 当前底层 UserInfoService 还未提供全局接收选项写接口，这里先保留 facade 边界。
        if (session == null) {
            throw new IllegalStateException("session invalid");
        }
    }
}
