package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.apiserver.auth.CurrentPrincipalArgumentResolver;
import com.cheeseocean.im.apiserver.exception.ApiExceptionHandler;
import com.cheeseocean.im.business.model.BlacklistActionRequest;
import com.cheeseocean.im.business.service.friend.FriendRelationServiceImpl;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BlacklistControllerTest {

    @Test
    void listShouldReturnBlockedUserIds() throws Exception {
        FriendRelationServiceImpl service = mock(FriendRelationServiceImpl.class);
        when(service.listBlockedUserIds("u100")).thenReturn(List.of("u200"));

        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/im/blacklist").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("u200"));
    }

    @Test
    void blockShouldReturnCreated() throws Exception {
        FriendRelationServiceImpl service = mock(FriendRelationServiceImpl.class);
        BlacklistActionRequest request = new BlacklistActionRequest();
        request.setTargetUserId("u200");

        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/im/blacklist")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isCreated());

        verify(service).blockUser("u100", "u200");
    }

    @Test
    void unblockShouldReturnNoContent() throws Exception {
        FriendRelationServiceImpl service = mock(FriendRelationServiceImpl.class);

        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(delete("/api/im/blacklist/u200").header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent());

        verify(service).unblockUser("u100", "u200");
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }

    private static MockMvc mockMvc(FriendRelationServiceImpl service) {
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));
        return MockMvcBuilders.standaloneSetup(new BlacklistController(service))
                .setCustomArgumentResolvers(new CurrentPrincipalArgumentResolver(resolver))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
