package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.apiserver.auth.CurrentPrincipalArgumentResolver;
import com.cheeseocean.im.apiserver.facade.UserFacade;
import com.cheeseocean.im.apiserver.model.response.UserSettingsResponse;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    @Test
    void getShouldReturnCurrentSettings() throws Exception {
        UserFacade userFacade = mock(UserFacade.class);
        UserSettingsResponse response = new UserSettingsResponse();
        response.setReceiveOpt(2);
        when(userFacade.getUserSettings(any(SessionPrincipal.class))).thenReturn(response);

        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userFacade))
                .setCustomArgumentResolvers(new CurrentPrincipalArgumentResolver(resolver))
                .build();

        mockMvc.perform(get("/api/im/user/settings").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiveOpt").value(2));
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }
}
