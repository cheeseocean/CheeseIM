package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.user.UserInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserSettingsControllerTest {

    @Test
    void getShouldReturnCurrentSettings() throws Exception {
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        UserInfoService userInfoService = mock(UserInfoService.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));
        when(userInfoService.getReceiveOptions("u100")).thenReturn(2);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserSettingsController(resolver, userInfoService)).build();

        mockMvc.perform(get("/api/im/user/settings").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalRecvMsgOpt").value(2));
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }
}
