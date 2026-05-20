package com.cheeseocean.im.business.service;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.template.QuickConfig;
import com.cheeseocean.im.business.service.friend.FriendRealtimeNotifier;
import com.cheeseocean.im.business.service.friend.FriendRelationServiceImpl;
import com.cheeseocean.im.common.api.business.domain.FriendRequest;
import com.cheeseocean.im.common.api.business.domain.Friendship;
import com.cheeseocean.im.common.api.enums.HandleResultEnum;
import com.cheeseocean.im.common.core.business.repository.BlacklistRepository;
import com.cheeseocean.im.common.core.business.repository.FriendRequestRepository;
import com.cheeseocean.im.common.core.business.repository.FriendshipRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendRelationServiceImplTest {

    @Test
    void sendFriendRequestShouldReturnOutgoingPendingRequest() {
        FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);
        FriendRequestRepository requestRepository = mock(FriendRequestRepository.class);
        BlacklistRepository blacklistRepository = mock(BlacklistRepository.class);
        FriendRealtimeNotifier notifier = mock(FriendRealtimeNotifier.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));
        when(friendshipRepository.find("userA", "userB")).thenReturn(null);
        when(requestRepository.find("userB", "userA")).thenReturn(null);
        when(requestRepository.find("userA", "userB")).thenReturn(null);

        FriendRelationServiceImpl service = new FriendRelationServiceImpl(friendshipRepository, requestRepository, blacklistRepository, notifier, cacheManager);

        FriendRequest request = service.sendFriendRequest("userA", "userB", "hello there");

        assertEquals("userA", request.getFromUserId());
        assertEquals("userB", request.getToUserId());
        assertEquals(HandleResultEnum.PENDING, request.getHandleResult());
        assertEquals("hello there", request.getReqMsg());
        verify(requestRepository).update(any(FriendRequest.class));
        verify(notifier).friendRequestCreated("userA", "userB",  "hello there");
    }

    @Test
    void sendFriendRequestShouldRejectWhenReversePendingAlreadyExists() {
        FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);
        FriendRequestRepository requestRepository = mock(FriendRequestRepository.class);
        BlacklistRepository blacklistRepository = mock(BlacklistRepository.class);
        FriendRealtimeNotifier notifier = mock(FriendRealtimeNotifier.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));
        when(friendshipRepository.find("userA", "userB")).thenReturn(null);
        when(requestRepository.find("userB", "userA")).thenReturn(pendingRequest("userB", "userA", "hello"));

        FriendRelationServiceImpl service = new FriendRelationServiceImpl(friendshipRepository, requestRepository, blacklistRepository, notifier, cacheManager);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.sendFriendRequest("userA", "userB", "hello there")
        );

        assertEquals("incoming friend request pending; accept instead", error.getMessage());
        verify(requestRepository, never()).update(any(FriendRequest.class));
    }

    @Test
    void listIncomingAndOutgoingRequestsShouldMapDirectionAndMessage() {
        FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);
        FriendRequestRepository requestRepository = mock(FriendRequestRepository.class);
        BlacklistRepository blacklistRepository = mock(BlacklistRepository.class);
        FriendRealtimeNotifier notifier = mock(FriendRealtimeNotifier.class);
        CacheManager cacheManager = mock(CacheManager.class);
        @SuppressWarnings("unchecked")
        Cache<String, List<FriendRequest>> incomingCache = mock(Cache.class);
        @SuppressWarnings("unchecked")
        Cache<String, List<FriendRequest>> outgoingCache = mock(Cache.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class)))
                .thenReturn(mock(Cache.class))
                .thenReturn(mock(Cache.class))
                .thenReturn(incomingCache)
                .thenReturn(outgoingCache);
        when(requestRepository.findIncoming("userA", List.of(0), 0, 0)).thenReturn(List.of(
                pendingRequest("userB", "userA", "please add me")
        ));
        when(requestRepository.findOutgoing("userA", List.of(0), 0, 0)).thenReturn(List.of(
                pendingRequest("userA", "userC", "ping")
        ));
        when(incomingCache.computeIfAbsent(anyString(), any())).thenAnswer(invocation ->
                requestRepository.findIncoming(invocation.getArgument(0), List.of(0), 0, 0)
        );
        when(outgoingCache.computeIfAbsent(anyString(), any())).thenAnswer(invocation ->
                requestRepository.findOutgoing(invocation.getArgument(0), List.of(0), 0, 0)
        );

        FriendRelationServiceImpl service = new FriendRelationServiceImpl(friendshipRepository, requestRepository, blacklistRepository, notifier, cacheManager);

        List<FriendRequest> incoming = service.listIncomingRequests("userA");
        List<FriendRequest> outgoing = service.listOutgoingRequests("userA");

        assertEquals("userB", incoming.get(0).getFromUserId());
        assertEquals("please add me", incoming.get(0).getReqMsg());
        assertEquals("userC", outgoing.get(0).getToUserId());
        assertEquals("ping", outgoing.get(0).getReqMsg());
    }

    @Test
    void acceptRejectAndCancelShouldUpdateRepositoriesAndReturnExpectedPayloads() {
        FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);
        FriendRequestRepository requestRepository = mock(FriendRequestRepository.class);
        BlacklistRepository blacklistRepository = mock(BlacklistRepository.class);
        FriendRealtimeNotifier notifier = mock(FriendRealtimeNotifier.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));
        when(requestRepository.find("userB", "userA"))
                .thenReturn(pendingRequest("userB", "userA", "please"))
                .thenReturn(pendingRequest("userB", "userA", "please"));
        when(requestRepository.find("userA", "userC")).thenReturn(pendingRequest("userA", "userC", "ping"));

        FriendRelationServiceImpl service = new FriendRelationServiceImpl(friendshipRepository, requestRepository, blacklistRepository, notifier, cacheManager);

        Friendship accepted = service.acceptFriendRequest("userA", "userB");
        FriendRequest rejected = service.rejectFriendRequest("userA", "userB");
        FriendRequest cancelled = service.cancelFriendRequest("userA", "userC");

        assertEquals("userA", accepted.getUserId());
        assertEquals("userB", accepted.getFriendId());
        assertEquals(HandleResultEnum.REJECTED, rejected.getHandleResult());
        assertEquals("userB", rejected.getFromUserId());
        assertEquals(HandleResultEnum.REJECTED, cancelled.getHandleResult());
        assertEquals("userC", cancelled.getToUserId());
        verify(friendshipRepository).saveAll(anyList());
        verify(requestRepository).update(any(FriendRequest.class));
        verify(requestRepository).updateFields(eq("userB"), eq("userA"), anyMap());
        verify(requestRepository).updateFields(eq("userA"), eq("userC"), anyMap());
        verify(notifier).friendRequestAccepted("userA", "userB");
        verify(notifier).friendRequestRejected("userA", "userB");
        verify(notifier).friendRequestCancelled("userA", "userC");
    }

    @Test
    void blockAndUnblockShouldNotifyRealtimeChannel() {
        FriendshipRepository friendshipRepository = mock(FriendshipRepository.class);
        FriendRequestRepository requestRepository = mock(FriendRequestRepository.class);
        BlacklistRepository blacklistRepository = mock(BlacklistRepository.class);
        FriendRealtimeNotifier notifier = mock(FriendRealtimeNotifier.class);
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getOrCreateCache(any(QuickConfig.class))).thenReturn(mock(Cache.class));

        FriendRelationServiceImpl service = new FriendRelationServiceImpl(friendshipRepository, requestRepository, blacklistRepository, notifier, cacheManager);

        service.blockUser("userA", "userB");
        service.unblockUser("userA", "userB");

        verify(blacklistRepository).blockUser("userA", "userB");
        verify(blacklistRepository).unblockUser("userA", "userB");
        verify(notifier).blackAdded("userA", "userB");
        verify(notifier).blackDeleted("userA", "userB");
    }

    private static FriendRequest pendingRequest(String fromUserId, String toUserId, String reqMsg) {
        FriendRequest request = new FriendRequest();
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setReqMsg(reqMsg);
        request.setHandleResult(HandleResultEnum.PENDING);
        return request;
    }
}
