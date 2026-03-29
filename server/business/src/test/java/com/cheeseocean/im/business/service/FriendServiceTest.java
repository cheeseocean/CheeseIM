package com.cheeseocean.im.business.service;

import com.cheeseocean.im.common.core.auth.FriendRequestSummary;
import com.cheeseocean.im.common.core.auth.FriendSummary;
import com.cheeseocean.im.business.repository.FriendRepository;
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
        when(repository.hasIncomingRequest("userA", "userB")).thenReturn(false);

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
        when(repository.hasIncomingRequest("userA", "userB")).thenReturn(true);

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
        when(repository.listIncomingRequests("userA")).thenReturn(List.of(
                new FriendRepository.FriendRequestRecord("userB", "userA", "please add me")
        ));
        when(repository.listOutgoingRequests("userA")).thenReturn(List.of(
                new FriendRepository.FriendRequestRecord("userA", "userC", "ping")
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
        when(repository.hasIncomingRequest("userA", "userB")).thenReturn(true);
        when(repository.hasOutgoingRequest("userA", "userC")).thenReturn(true);

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
        verify(repository).rejectPendingRequest("userB", "userA");
        verify(repository).cancelPendingRequest("userA", "userC");
        verify(notifier).friendRequestAccepted("userA", "userB");
        verify(notifier).friendRequestRejected("userA", "userB");
        verify(notifier).friendRequestCancelled("userA", "userC");
    }
}
