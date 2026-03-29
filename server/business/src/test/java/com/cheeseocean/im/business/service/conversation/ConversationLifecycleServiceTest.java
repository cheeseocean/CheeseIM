package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.business.repository.ConversationOffsetRangeRepository;
import com.cheeseocean.im.business.repository.UserConversationStateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConversationLifecycleServiceTest {

    @Test
    void createSingleChatConversationShouldInitializeStateAndOffsetsForBothSides() {
        UserConversationStateRepository stateRepository = mock(UserConversationStateRepository.class);
        ConversationOffsetRangeRepository offsetRepository = mock(ConversationOffsetRangeRepository.class);
        ConversationSettingsNotifier settingsNotifier = mock(ConversationSettingsNotifier.class);

        ConversationLifecycleService service = new ConversationLifecycleService(
                stateRepository, offsetRepository, settingsNotifier
        );

        service.createSingleChatConversation("userA", "userB", "c_userA_userB", SessionType.SINGLE.getCode());

        verify(stateRepository).createIfAbsent(any());
        verify(offsetRepository).createIfAbsent("userA", "c_userA_userB");
        verify(offsetRepository).createIfAbsent("userB", "c_userA_userB");
    }

    @Test
    void createNotificationConversationShouldOnlyInitializeReceiver() {
        UserConversationStateRepository stateRepository = mock(UserConversationStateRepository.class);
        ConversationOffsetRangeRepository offsetRepository = mock(ConversationOffsetRangeRepository.class);
        ConversationSettingsNotifier settingsNotifier = mock(ConversationSettingsNotifier.class);

        ConversationLifecycleService service = new ConversationLifecycleService(
                stateRepository, offsetRepository, settingsNotifier
        );

        service.createSingleChatConversation("sys", "userB", "n_userB", SessionType.NOTIFICATION.getCode());

        verify(offsetRepository).createIfAbsent("userB", "n_userB");
        verify(offsetRepository, never()).createIfAbsent(eq("sys"), eq("n_userB"));
    }

    @Test
    void createGroupConversationShouldInitializeStateAndOffsetsForAllMembers() {
        UserConversationStateRepository stateRepository = mock(UserConversationStateRepository.class);
        ConversationOffsetRangeRepository offsetRepository = mock(ConversationOffsetRangeRepository.class);
        ConversationSettingsNotifier settingsNotifier = mock(ConversationSettingsNotifier.class);

        ConversationLifecycleService service = new ConversationLifecycleService(
                stateRepository, offsetRepository, settingsNotifier
        );

        service.createGroupChatConversations("g1", "g_g1", List.of("u1", "u2"));

        verify(offsetRepository).createIfAbsent("u1", "g_g1");
        verify(offsetRepository).createIfAbsent("u2", "g_g1");
    }

    @Test
    void setConversationsShouldNotifyOnlyWhenRecvMsgOptChanges() {
        UserConversationStateRepository stateRepository = mock(UserConversationStateRepository.class);
        ConversationOffsetRangeRepository offsetRepository = mock(ConversationOffsetRangeRepository.class);
        ConversationSettingsNotifier settingsNotifier = mock(ConversationSettingsNotifier.class);

        ConversationLifecycleService service = new ConversationLifecycleService(
                stateRepository, offsetRepository, settingsNotifier
        );

        SetConversationRequest request = new SetConversationRequest();
        request.setConversationId("c1");
        request.setConversationType(SessionType.SINGLE.getCode());
        request.setTargetId("u2");
        request.setPinned(true);

        service.setConversations(List.of("u1"), request);

        verify(settingsNotifier, never()).notifyRecvMsgOptChanged(any(), any(), any(Integer.class));
    }
}
