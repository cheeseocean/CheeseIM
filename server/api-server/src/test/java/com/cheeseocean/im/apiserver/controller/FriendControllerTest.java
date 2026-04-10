package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.business.model.AddFriendRequest;
import com.cheeseocean.im.business.model.FriendRequestActionRequest;
import com.cheeseocean.im.business.service.friend.FriendRelationServiceImpl;
import com.cheeseocean.im.common.api.business.domain.FriendRequest;
import com.cheeseocean.im.common.api.business.domain.Friendship;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FriendControllerTest {

    @Test
    void listShouldReturnFriends() throws Exception {
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        FriendRelationServiceImpl service = mock(FriendRelationServiceImpl.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));
        when(service.listFriends("u100")).thenReturn(List.of(friendship("u100", "u200")));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FriendController(resolver, service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(get("/api/im/friends").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("u100"))
                .andExpect(jsonPath("$[0].friendId").value("u200"));
    }

    @Test
    void incomingShouldReturnRequests() throws Exception {
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        FriendRelationServiceImpl service = mock(FriendRelationServiceImpl.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));
        when(service.listIncomingRequests("u100")).thenReturn(List.of(friendRequest("u200", "u100")));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FriendController(resolver, service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(get("/api/im/friends/requests/incoming").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromUserId").value("u200"))
                .andExpect(jsonPath("$[0].toUserId").value("u100"));
    }

    @Test
    void addShouldDelegateToService() throws Exception {
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        FriendRelationServiceImpl service = mock(FriendRelationServiceImpl.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));
        when(service.sendFriendRequest("u100", "u200", "hi")).thenReturn(friendRequest("u100", "u200"));

        AddFriendRequest request = new AddFriendRequest();
        request.setFriendUserId("u200");
        request.setRequestMessage("hi");

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FriendController(resolver, service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

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
        AccessTokenSessionResolver resolver = mock(AccessTokenSessionResolver.class);
        FriendRelationServiceImpl service = mock(FriendRelationServiceImpl.class);
        when(resolver.resolve("Bearer token")).thenReturn(session("u100"));
        when(service.acceptFriendRequest("u100", "u200")).thenReturn(friendship("u100", "u200"));

        FriendRequestActionRequest request = new FriendRequestActionRequest();
        request.setFriendUserId("u200");

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FriendController(resolver, service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/api/im/friends/requests/accept")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content(new ObjectMapper().writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendId").value("u200"));
    }

    private static SessionPrincipal session(String userId) {
        SessionPrincipal session = new SessionPrincipal();
        session.setUserId(userId);
        session.setStatus(SessionStatus.ACTIVE);
        return session;
    }

    private static Friendship friendship(String ownerUserId, String friendUserId) {
        Friendship friendship = new Friendship();
        friendship.setUserId(ownerUserId);
        friendship.setFriendId(friendUserId);
        return friendship;
    }

    private static FriendRequest friendRequest(String fromUserId, String toUserId) {
        FriendRequest request = new FriendRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        return request;
    }
}
