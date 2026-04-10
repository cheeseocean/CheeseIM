package com.cheeseocean.im.apiserver.controller;

import com.cheeseocean.im.apiserver.facade.FriendFacade;
import com.cheeseocean.im.apiserver.model.request.HandleFriendRequestRequest;
import com.cheeseocean.im.apiserver.model.request.SendFriendRequestRequest;
import com.cheeseocean.im.apiserver.model.response.FriendRequestResponse;
import com.cheeseocean.im.apiserver.model.response.FriendshipResponse;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/im/friends")
public class FriendController {

    private final FriendFacade friendFacade;

    public FriendController(FriendFacade friendFacade) {
        this.friendFacade = friendFacade;
    }

    @GetMapping
    public List<FriendshipResponse> list(SessionPrincipal session) {
        return friendFacade.listFriends(session);
    }

    @GetMapping("/requests/incoming")
    public List<FriendRequestResponse> incoming(SessionPrincipal session) {
        return friendFacade.listIncomingRequests(session);
    }

    @GetMapping("/requests/outgoing")
    public List<FriendRequestResponse> outgoing(SessionPrincipal session) {
        return friendFacade.listOutgoingRequests(session);
    }

    @PostMapping("/requests")
    public FriendRequestResponse add(SessionPrincipal session,
                                     @RequestBody SendFriendRequestRequest request) {
        return friendFacade.sendFriendRequest(session, request);
    }

    @PostMapping("/requests/accept")
    public FriendshipResponse accept(SessionPrincipal session,
                                     @RequestBody HandleFriendRequestRequest request) {
        return friendFacade.acceptFriendRequest(session, request);
    }

    @PostMapping("/requests/reject")
    public FriendRequestResponse reject(SessionPrincipal session,
                                        @RequestBody HandleFriendRequestRequest request) {
        return friendFacade.rejectFriendRequest(session, request);
    }

    @PostMapping("/requests/cancel")
    public FriendRequestResponse cancel(SessionPrincipal session,
                                        @RequestBody HandleFriendRequestRequest request) {
        return friendFacade.cancelFriendRequest(session, request);
    }
}
