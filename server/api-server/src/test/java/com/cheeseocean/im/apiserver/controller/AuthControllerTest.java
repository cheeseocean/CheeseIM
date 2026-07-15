package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.exception.ApiExceptionHandler;
import com.cheeseocean.im.apiserver.model.request.KickoffDeviceRequest;
import com.cheeseocean.im.apiserver.model.request.LogoutRequest;
import com.cheeseocean.im.common.api.auth.AuthenticationCommand;
import com.cheeseocean.im.common.api.auth.AuthenticationResult;
import com.cheeseocean.im.common.api.auth.AuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthenticationService sessionLifecycleService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sessionLifecycleService = mock(AuthenticationService.class);
        AuthController controller = new AuthController(sessionLifecycleService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void loginShouldReturnAuthResponse() throws Exception {
        AuthenticationResult response = new AuthenticationResult();
        response.setAccessToken("access");
        response.setRefreshToken("refresh");
        response.setSessionId("sid");
        response.setUserId("u100");
        when(sessionLifecycleService.login(any(AuthenticationCommand.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u100","platformId":1,"deviceId":"dev-1","clientVersion":"1.0.0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.userId").value("u100"));
    }

    @Test
    void refreshShouldReturnAuthResponse() throws Exception {
        AuthenticationResult response = new AuthenticationResult();
        response.setAccessToken("access-2");
        response.setRefreshToken("refresh-2");
        response.setSessionId("sid");
        response.setUserId("u100");
        when(sessionLifecycleService.refresh(any(String.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"refresh"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-2"));
    }

    @Test
    void logoutShouldReturnSuccess() throws Exception {
        LogoutRequest request = new LogoutRequest();
        request.setSessionId("sid-1");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionLifecycleService).logout("sid-1");
    }

    @Test
    void kickoffDeviceShouldReturnSuccess() throws Exception {
        KickoffDeviceRequest request = new KickoffDeviceRequest();
        request.setUserId("u100");

        mockMvc.perform(post("/api/auth/devices/dev-1/kickoff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionLifecycleService).kickoffDevice("u100", "dev-1");
    }

    @Test
    void kickoffAllShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/kickoff-all/u100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sessionLifecycleService).kickoffAll("u100");
    }

    @Test
    void illegalStateShouldMapToBadRequest() throws Exception {
        doThrow(new IllegalStateException("bad request"))
                .when(sessionLifecycleService).login(any(AuthenticationCommand.class));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"u100","platformId":1,"deviceId":"dev-1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("bad request"));
    }
}
