package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.apiserver.auth.CurrentPrincipalArgumentResolver;
import com.cheeseocean.im.apiserver.exception.ApiExceptionHandler;
import com.cheeseocean.im.apiserver.facade.FriendFacade;
import com.cheeseocean.im.apiserver.model.request.HandleFriendRequestRequest;
import com.cheeseocean.im.apiserver.model.request.SendFriendRequestRequest;
import com.cheeseocean.im.apiserver.model.response.FriendRequestResponse;
import com.cheeseocean.im.apiserver.model.response.FriendshipResponse;
import com.cheeseocean.im.common.api.enums.SessionStatus;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendControllerTest {

    @Test
    void listShouldReturnFriends() throws Exception {
        FriendFacade facade = mock(FriendFacade.class);
        when(facade.listFriends(any(SessionPrincipal.class)))
                .thenReturn(List.of(friendship("u100", "u200")));

        MockMvc mockMvc = mockMvc(facade);

        mockMvc.perform(get("/api/im/friends").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("u100"))
                .andExpect(jsonPath("$[0].friendId").value("u200"));
    }

    @Test
    void incomingShouldReturnRequests() throws Exception {
        FriendFacade facade = mock(FriendFacade.class);
        when(facade.listIncomingRequests(any(SessionPrincipal.class)))
                .thenReturn(List.of(friendRequest("u200", "u100")));

        MockMvc mockMvc = mockMvc(facade);

        mockMvc.perform(get("/api/im/friends/requests/incoming").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromUserId").value("u200"))
                .andExpect(jsonPath("$[0].toUserId").value("u100"));
    }

    @Test
    void addShouldDelegateToService() throws Exception {
        FriendFacade facade = mock(FriendFacade.class);
        when(facade.sendFriendRequest(any(SessionPrincipal.class), any(SendFriendRequestRequest.class)))
                .thenReturn(friendRequest("u100", "u200"));

        SendFriendRequestRequest request = new SendFriendRequestRequest();
        request.setFriendUserId("u200");
        request.setRequestMessage("hi");

        MockMvc mockMvc = mockMvc(facade);

        mockMvc.perform(post("/api/im/friends/requests")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromUserId").value("u100"))
                .andExpect(jsonPath("$.toUserId").value("u200"));
    }

    @Test
    void acceptShouldReturnFriendship() throws Exception {
        FriendFacade facade = mock(FriendFacade.class);
        when(facade.acceptFriendRequest(any(SessionPrincipal.class), any(HandleFriendRequestRequest.class)))
                .thenReturn(friendship("u100", "u200"));

        HandleFriendRequestRequest request = new HandleFriendRequestRequest();
        request.setFriendUserId("u200");

        MockMvc mockMvc = mockMvc(facade);

        mockMvc.perform(post("/api/im/friends/requests/accept")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendId").value("u200"));
    }

    private static FriendshipResponse friendship(String ownerUserId, String friendUserId) {
        FriendshipResponse friendship = new FriendshipResponse();
        friendship.setUserId(ownerUserId);
        friendship.setFriendId(friendUserId);
        return friendship;
    }

    private static FriendRequestResponse friendRequest(String fromUserId, String toUserId) {
        FriendRequestResponse request = new FriendRequestResponse();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        return request;
    }

    private static MockMvc mockMvc(FriendFacade facade) {
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));
        return MockMvcBuilders.standaloneSetup(new FriendController(facade))
                .setCustomArgumentResolvers(new CurrentPrincipalArgumentResolver(resolver))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }
}
