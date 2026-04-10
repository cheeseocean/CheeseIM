package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
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
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        FriendRelationServiceImpl service = mock(FriendRelationServiceImpl.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));
        when(service.listBlockedUserIds("u100")).thenReturn(List.of("u200"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BlacklistController(resolver, service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(get("/api/im/blacklist").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("u200"));
    }

    @Test
    void blockShouldReturnCreated() throws Exception {
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        FriendRelationServiceImpl service = mock(FriendRelationServiceImpl.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));
        BlacklistActionRequest request = new BlacklistActionRequest();
        request.setTargetUserId("u200");

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BlacklistController(resolver, service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/api/im/blacklist")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isCreated());

        verify(service).blockUser("u100", "u200");
    }

    @Test
    void unblockShouldReturnNoContent() throws Exception {
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        FriendRelationServiceImpl service = mock(FriendRelationServiceImpl.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BlacklistController(resolver, service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

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
}
