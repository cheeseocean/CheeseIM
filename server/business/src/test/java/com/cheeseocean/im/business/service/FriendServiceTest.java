package com.cheeseocean.im.business.service;

import com.cheeseocean.im.common.api.dto.user.FriendRequestSummary;
import com.cheeseocean.im.common.api.dto.user.FriendSummary;
import com.cheeseocean.im.common.core.business.domain.FriendRequest;
import com.cheeseocean.im.common.core.business.repository.FriendRepository;
import com.cheeseocean.im.business.service.friend.FriendRealtimeNotifier;
import com.cheeseocean.im.business.service.friend.FriendService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendServiceTest {

    @Test
    void sendFriendRequestShouldReturnOutgoingPendingRequest() {
        FriendRepository       repository = mock(FriendRepository.class);
        FriendRealtimeNotifier notifier   = mock(FriendRealtimeNotifier.class);
        when(repository.areAcceptedFriends("userA", "userB")).thenReturn(false);
        when(repository.hasIncomingPendingRequest("userA", "userB")).thenReturn(false);
        when(repository.findPendingRequest("userA", "userB")).thenReturn(java.util.Optional.empty());

        FriendService service = new FriendService(repository, notifier);

        FriendRequestSummary summary = service.sendFriendRequest("userA", "userB", "hello there");

        assertEquals("userB", summary.getUserId());
        assertEquals("outgoing", summary.getDirection());
        assertEquals("pending", summary.getStatus());
        assertEquals("hello there", summary.getRequestMessage());
        verify(repository).savePendingRequest("userA", "userB", "hello there");
        verify(notifier).friendRequestCreated("userA", "userB");
    }

    @Test
    void sendFriendRequestShouldRejectWhenReversePendingAlreadyExists() {
        FriendRepository repository = mock(FriendRepository.class);
        FriendRealtimeNotifier notifier = mock(FriendRealtimeNotifier.class);
        when(repository.areAcceptedFriends("userA", "userB")).thenReturn(false);
        when(repository.hasIncomingPendingRequest("userA", "userB")).thenReturn(true);

        FriendService service = new FriendService(repository, notifier);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.sendFriendRequest("userA", "userB", "hello there")
        );

        assertEquals("incoming friend request pending; accept instead", error.getMessage());
        verify(repository, never()).savePendingRequest(anyString(), anyString(), anyString());
    }

        @Test
    void listIncomingAndOutgoingRequestsShouldMapDirectionAndMessage() {
        FriendRepository repository = mock(FriendRepository.class);
        FriendRealtimeNotifier notifier = mock(FriendRealtimeNotifier.class);
        when(repository.listIncomingPendingRequests("userA")).thenReturn(List.of(
                pendingRequest("userB", "userA", "please add me")
        ));
        when(repository.listOutgoingPendingRequests("userA")).thenReturn(List.of(
                pendingRequest("userA", "userC", "ping")
        ));

        FriendService service = new FriendService(repository, notifier);

        List<FriendRequestSummary> incoming = service.listIncomingRequests("userA");
        List<FriendRequestSummary> outgoing = service.listOutgoingRequests("userA");

        assertEquals("incoming", incoming.get(0).getDirection());
        assertEquals("please add me", incoming.get(0).getRequestMessage());
        assertEquals("outgoing", outgoing.get(0).getDirection());
        assertEquals("ping", outgoing.get(0).getRequestMessage());
    }

    @Test
    void acceptRejectAndCancelShouldUpdateRepositoryAndReturnExpectedPayloads() {
        FriendRepository repository = mock(FriendRepository.class);
        FriendRealtimeNotifier notifier = mock(FriendRealtimeNotifier.class);
        when(repository.hasIncomingPendingRequest("userA", "userB")).thenReturn(true);
        when(repository.hasOutgoingPendingRequest("userA", "userC")).thenReturn(true);

        FriendService service = new FriendService(repository, notifier);

        FriendSummary accepted = service.acceptFriendRequest("userA", "userB");
        FriendRequestSummary rejected = service.rejectFriendRequest("userA", "userB");
        FriendRequestSummary cancelled = service.cancelFriendRequest("userA", "userC");

        assertEquals("userB", accepted.getUserId());
        assertEquals("rejected", rejected.getStatus());
        assertEquals("incoming", rejected.getDirection());
        assertEquals("cancelled", cancelled.getStatus());
        assertEquals("outgoing", cancelled.getDirection());
        verify(repository).acceptFriendPair("userA", "userB");
        verify(repository).rejectRequest("userB", "userA");
        verify(repository).cancelRequest("userA", "userC");
        verify(notifier).friendRequestAccepted("userA", "userB");
        verify(notifier).friendRequestRejected("userA", "userB");
        verify(notifier).friendRequestCancelled("userA", "userC");
    }

    private static FriendRequest pendingRequest(String fromUserId, String toUserId, String reqMsg) {
        FriendRequest request = new FriendRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setReqMsg(reqMsg);
        return request;
    }
}
