package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.business.service.permission.ConversationPermissionServiceImpl;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.business.domain.ConversationVersionLog;
import com.cheeseocean.im.common.api.conversation.ReadStateService;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.common.api.dto.conversation.SeqRangeRequest;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ConversationVersionOperation;
import com.cheeseocean.im.common.api.enums.ErrorCode;
import com.cheeseocean.im.common.api.exception.BusinessException;
import com.cheeseocean.im.common.api.message.MessageHistoryQueryService;
import com.cheeseocean.im.common.core.business.repository.ConversationDeliveryPreferenceRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationSequenceRepository;
import com.cheeseocean.im.common.core.business.repository.ConversationVersionLogRepository;
import com.cheeseocean.im.common.core.business.repository.GroupMemberRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import com.cheeseocean.im.common.core.business.repository.UserConversationSyncPointRepository;
import com.cheeseocean.im.common.core.cache.CacheRegion;
import com.cheeseocean.im.common.core.cache.CacheStore;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationAuthorizationTest {
    private final UserConversationRepository views = mock(UserConversationRepository.class);
    private final ConversationVersionLogRepository logs = mock(ConversationVersionLogRepository.class);
    private final ConversationDeliveryPreferenceRepository preferences = mock(ConversationDeliveryPreferenceRepository.class);
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final CacheRegion<UserConversation> detail = mock(CacheRegion.class);
    private final CacheRegion<List<String>> ids = mock(CacheRegion.class);
    private ConversationServiceImpl conversations;

    @BeforeEach
    void setUp() {
        CacheStore cache = mock(CacheStore.class);
        when(cache.region(anyString(), eq(UserConversation.class), any())).thenReturn(detail);
        when(cache.region(anyString(), eq(Long.class), any())).thenReturn(mock(CacheRegion.class));
        when(cache.listRegion(anyString(), eq(String.class), any())).thenReturn(ids);
        when(detail.getAll(any())).thenReturn(Map.of());
        when(detail.getOrLoad(anyString(), any())).thenAnswer(invocation -> {
            Supplier<UserConversation> loader = invocation.getArgument(1);
            return loader.get();
        });
        conversations = new ConversationServiceImpl(views, logs, preferences, cache,
                new ConversationPermissionServiceImpl(members));
    }

    @Test
    void shouldRejectForgedSettingsBeforeAnyPersistenceOrCacheAccess() {
        assertThatThrownBy(() -> conversations.setConversations(List.of("attacker"), settings("s:A:B")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONVERSATION_ACCESS_DENIED));

        verifyNoInteractions(views, logs, preferences, detail, ids);
    }

    @Test
    void settingsShouldNotCreateEvenAnOwnedMissingConversation() {
        assertThatThrownBy(() -> conversations.setConversations(List.of("A"), settings("s:A:B")))
                .isInstanceOf(BusinessException.class);

        verify(views).findOne("A", "s:A:B");
        verify(views, never()).createIfAbsent(any());
        verify(views, never()).updateFields(anyString(), anyString(), anyMap());
        verifyNoInteractions(logs, preferences);
    }

    @Test
    void mixedOwnerBatchShouldBeValidatedBeforeAnyWrite() {
        when(views.findOne("A", "s:A:B")).thenReturn(view("A", "s:A:B", "B"));
        SetConversationRequest request = settings("s:A:B");
        request.setRecvMsgOpt(2);

        assertThatThrownBy(() -> conversations.setConversations(List.of("A", "C"), request))
                .isInstanceOf(BusinessException.class);

        verify(views, never()).updateFields(anyString(), anyString(), anyMap());
        verify(views, never()).createIfAbsent(any());
        verifyNoInteractions(logs, preferences);
    }

    @Test
    void ownedExistingConversationShouldOnlyUpdateSettings() {
        when(views.findOne("A", "s:A:B")).thenReturn(view("A", "s:A:B", "B"));
        SetConversationRequest request = settings("s:A:B");
        request.setConversationType(ChatType.PRIVATE.getCode());
        request.setTargetId("B");
        request.setPinned(true);
        request.setRecvMsgOpt(2);

        conversations.setConversations(List.of("A", "A"), request);

        verify(views).updateFields("A", "s:A:B", Map.of("pinned", true, "receiveOpt", 2));
        verify(views, never()).createIfAbsent(any());
        verify(preferences).setReceiveOptions(List.of("A"), "s:A:B", 2);
        verify(logs).append("A", "s:A:B", ConversationVersionOperation.UPDATE);
    }

    @Test
    void mismatchedIdentityOrInvalidOptionShouldNotWrite() {
        when(views.findOne("A", "s:A:B")).thenReturn(view("A", "s:A:B", "B"));
        SetConversationRequest request = settings("s:A:B");
        request.setTargetId("C");
        assertThatThrownBy(() -> conversations.setConversations(List.of("A"), request))
                .isInstanceOf(BusinessException.class);
        request.setTargetId("B");
        request.setRecvMsgOpt(999);
        assertThatThrownBy(() -> conversations.setConversations(List.of("A"), request))
                .isInstanceOf(BusinessException.class);
        verify(views, never()).updateFields(anyString(), anyString(), anyMap());
        verifyNoInteractions(logs, preferences);
    }

    @Test
    void legacyCorruptedIdentityShouldBeReconstructedFromAuthorizedId() {
        UserConversation corrupt = view("A", "s:A:B", "third-party");
        corrupt.setChatType(ChatType.GROUP.getCode());
        when(views.findOne("A", "s:A:B")).thenReturn(corrupt);

        UserConversation visible = conversations.getConversation("A", "s:A:B");

        assertThat(visible.getChatType()).isEqualTo(ChatType.PRIVATE.getCode());
        assertThat(visible.getTargetId()).isEqualTo("B");
        assertThat(corrupt.getTargetId()).isEqualTo("third-party");
        SetConversationRequest request = settings("s:A:B");
        request.setTargetId("third-party");
        assertThatThrownBy(() -> conversations.setConversations(List.of("A"), request))
                .isInstanceOf(BusinessException.class);
        verify(views, never()).updateFields(anyString(), anyString(), anyMap());
    }

    @Test
    void persistedAndCachedForgedViewsShouldNotAuthorizeAnySyncPath() {
        // 保留旧伪视图，证明修复不依赖清库/缓存先失效。
        when(ids.get("attacker")).thenReturn(List.of("s:A:B", "n:A", "g:crew"));
        when(detail.getAll(any())).thenReturn(Map.of("attacker:s:A:B", view("attacker", "s:A:B", "B")));
        assertThat(conversations.getConversation("attacker", "s:A:B")).isNull();
        assertThat(conversations.getConversations("attacker", List.of("s:A:B"))).isEmpty();
        assertThat(conversations.getConversationIds("attacker")).isEmpty();
        assertThat(conversations.getPinnedConversationIds("attacker")).isEmpty();
        assertThat(conversations.getNotNotifyConversationIds("attacker")).isEmpty();

        ConversationSequenceRepository sequence = mock(ConversationSequenceRepository.class);
        UserConversationSyncPointRepository syncPoint = mock(UserConversationSyncPointRepository.class);
        ConversationStateStore hot = mock(ConversationStateStore.class);
        MessageHistoryQueryService history = mock(MessageHistoryQueryService.class);
        ConversationSyncServiceImpl sync = new ConversationSyncServiceImpl(conversations, sequence, syncPoint, hot,
                history, mock(ReadStateService.class));

        assertThat(sync.getConversationMaxSeqs("attacker", List.of())).isEmpty();
        assertThat(sync.getConversationMaxSeqs("attacker", List.of("s:A:B"))).isEmpty();
        assertThat(sync.getConversationReadSnapshots("attacker", List.of("s:A:B"))).isEmpty();
        assertThat(sync.pullMessagesBySeqRanges("attacker", List.of(range("s:A:B")), 10)
                .getMessagesByConversation().get("s:A:B")).isEmpty();

        verifyNoInteractions(sequence, syncPoint, hot, history);
        verify(views, never()).findByIds(anyString(), anyList());
        verify(detail, never()).getOrLoad(anyString(), any());
    }

    @Test
    void exitedGroupShouldBeHiddenEvenWhenViewAndIdsRemainCached() {
        UserConversation group = view("A", "g:crew", "crew");
        group.setChatType(ChatType.GROUP.getCode());
        when(members.existsByGroupAndUser("crew", "A")).thenReturn(true);
        when(views.findOne("A", "g:crew")).thenReturn(group);
        when(ids.get("A")).thenReturn(List.of("g:crew", "s:A:B"));
        assertThat(conversations.getConversation("A", "g:crew")).isNotNull();

        when(members.existsByGroupAndUser("crew", "A")).thenReturn(false);
        assertThat(conversations.getConversation("A", "g:crew")).isNull();
        assertThat(conversations.getConversationIds("A")).containsExactly("s:A:B");
        assertThat(conversations.getConversationIdsHash("A"))
                .isEqualTo((long) List.of("s:A:B").hashCode() & 0xFFFFFFFFL);
        assertThatThrownBy(() -> conversations.setConversations(List.of("A"), settings("g:crew")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(logs, preferences);
        verify(views, times(1)).findOne("A", "g:crew");
    }

    @Test
    void fullMetadataSyncShouldFilterLegacyForgedViews() {
        when(logs.findLatest("A")).thenReturn(Optional.empty());
        when(ids.get("A")).thenReturn(List.of("s:A:B", "s:C:D"));
        when(views.findAll("A")).thenReturn(List.of(view("A", "s:A:B", "B"), view("A", "s:C:D", "D")));

        assertThat(conversations.syncConversations("A", null, 0, 0).getInsert())
                .extracting(UserConversation::getConversationId).containsExactly("s:A:B");
    }

    @Test
    void incrementalMetadataSyncShouldDeleteInaccessibleViewsAndHideTheirReadSignals() {
        ConversationVersionLog latest = log("s:A:B", ConversationVersionOperation.UPDATE, 5);
        when(logs.findLatest("A")).thenReturn(Optional.of(latest));
        when(logs.findEarliest("A", "epoch")).thenReturn(Optional.of(log("s:A:B", ConversationVersionOperation.UPDATE, 1)));
        when(ids.get("A")).thenReturn(List.of("s:A:B", "s:C:D"));
        when(logs.findAfter("A", "epoch", 3, 200)).thenReturn(List.of(
                log("s:C:D", ConversationVersionOperation.UPDATE, 4),
                log("s:C:D", ConversationVersionOperation.READ_STATE_UPDATED, 5)));
        when(views.findByIds("A", List.of("s:C:D"))).thenReturn(List.of(view("A", "s:C:D", "D")));

        var result = conversations.syncConversations("A", "epoch", 3, 0);

        assertThat(result.getUpdate()).isEmpty();
        assertThat(result.getInsert()).isEmpty();
        assertThat(result.getDelete()).containsExactly("s:C:D");
        assertThat(result.getReadStateChangedConversationIds()).isEmpty();
    }

    private ConversationVersionLog log(String conversationId, ConversationVersionOperation operation, long version) {
        ConversationVersionLog log = new ConversationVersionLog();
        log.setConversationId(conversationId);
        log.setVersionId("epoch");
        log.setVersion(version);
        log.setOperation(operation);
        return log;
    }

    @Test
    void authorizedExistingConversationShouldStillPullHistory() {
        when(views.findByIds("A", List.of("s:A:B"))).thenReturn(List.of(view("A", "s:A:B", "B")));
        ConversationStateStore hot = mock(ConversationStateStore.class);
        when(hot.getUserMaxSeq("A", "s:A:B")).thenReturn(10L);
        MessageHistoryQueryService history = mock(MessageHistoryQueryService.class);
        when(history.pullMessagesBySeqRange("s:A:B", 1, 10, 10)).thenReturn(List.of());
        ConversationSyncServiceImpl sync = new ConversationSyncServiceImpl(conversations,
                mock(ConversationSequenceRepository.class), mock(UserConversationSyncPointRepository.class), hot,
                history, mock(ReadStateService.class));

        sync.pullMessagesBySeqRanges("A", List.of(range("s:A:B")), 10);

        verify(history).pullMessagesBySeqRange("s:A:B", 1, 10, 10);
    }

    private SetConversationRequest settings(String conversationId) {
        SetConversationRequest request = new SetConversationRequest();
        request.setConversationId(conversationId);
        return request;
    }

    private UserConversation view(String owner, String conversationId, String targetId) {
        UserConversation view = new UserConversation();
        view.setOwnerUserId(owner);
        view.setConversationId(conversationId);
        view.setChatType(ChatType.PRIVATE.getCode());
        view.setTargetId(targetId);
        return view;
    }

    private SeqRangeRequest range(String conversationId) {
        SeqRangeRequest range = new SeqRangeRequest();
        range.setConversationId(conversationId);
        range.setBeginSeq(1);
        range.setEndSeq(10);
        return range;
    }
}
