package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.conversation.ConversationSyncCommand;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.business.domain.UserConversationState;
import com.cheeseocean.im.business.repository.ConversationOffsetRangeRepository;
import com.cheeseocean.im.business.repository.UserConversationStateRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话生命周期核心服务。
 *
 * <p>统一承载显式建会话、消息路径懒创建、会话状态同步，避免
 * ConversationWrite/ConversationSync 两套实现持续漂移。
 */
@Service
public class ConversationLifecycleService {

    private final UserConversationStateRepository stateRepository;
    private final ConversationOffsetRangeRepository offsetRepository;
    private final ConversationSettingsNotifier settingsNotifier;

    public ConversationLifecycleService(UserConversationStateRepository stateRepository,
                                        ConversationOffsetRangeRepository offsetRepository,
                                        ConversationSettingsNotifier settingsNotifier) {
        this.stateRepository = stateRepository;
        this.offsetRepository = offsetRepository;
        this.settingsNotifier = settingsNotifier;
    }

    public void createIfNew(ConversationSyncCommand cmd) {
        if (!cmd.newConversation()) {
            return;
        }
        for (String participantId : cmd.allParticipants()) {
            createParticipantConversation(buildState(participantId, cmd));
        }
    }

    public void sync(ConversationSyncCommand cmd) {
        long newMaxSeq = cmd.latestMessage().getSeq();

        for (String participantId : cmd.allParticipants()) {
            offsetRepository.updateMaxSeq(participantId, cmd.conversationId(), newMaxSeq);
        }
    }

    public void createSingleChatConversation(String senderId, String recvId,
                                             String conversationId, int conversationType) {
        if (conversationType == SessionType.SINGLE.getCode()) {
            createParticipantConversation(buildExplicitState(senderId, conversationId, conversationType, recvId));
            createParticipantConversation(buildExplicitState(recvId, conversationId, conversationType, senderId));
            return;
        }

        createParticipantConversation(buildExplicitState(recvId, conversationId, conversationType, senderId));
    }

    public void createGroupChatConversations(String groupId, String conversationId, List<String> userIds) {
        for (String userId : userIds) {
            createParticipantConversation(
                    buildExplicitState(userId, conversationId, SessionType.GROUP.getCode(), groupId));
            offsetRepository.updateMaxSeq(userId, conversationId, 0);
        }
    }

    public void setConversations(List<String> userIds, SetConversationRequest request) {
        Map<String, Object> fields = buildUpdateFields(request);
        for (String userId : userIds) {
            stateRepository.upsertFields(
                    userId,
                    request.getConversationId(),
                    request.getConversationType(),
                    request.getTargetId(),
                    fields
            );
            offsetRepository.createIfAbsent(userId, request.getConversationId());
        }
        if (request.getRecvMsgOpt() != null) {
            for (String userId : userIds) {
                settingsNotifier.notifyRecvMsgOptChanged(
                        userId, request.getConversationId(), request.getRecvMsgOpt());
            }
        }
    }

    private void createParticipantConversation(UserConversationState state) {
        stateRepository.createIfAbsent(state);
        offsetRepository.createIfAbsent(state.getOwnerUserId(), state.getConversationId());
    }

    private UserConversationState buildExplicitState(String ownerUserId, String conversationId,
                                                     int conversationType, String targetId) {
        UserConversationState state = new UserConversationState();
        state.setOwnerUserId(ownerUserId);
        state.setConversationId(conversationId);
        state.setConversationType(conversationType);
        state.setTargetId(targetId);
        return state;
    }

    private UserConversationState buildState(String ownerId, ConversationSyncCommand cmd) {
        return buildExplicitState(ownerId, cmd.conversationId(), cmd.sessionType(), resolveTargetId(ownerId, cmd));
    }

    private String resolveTargetId(String ownerId, ConversationSyncCommand cmd) {
        if (cmd.sessionType() == SessionType.SINGLE.getCode()) {
            String senderId = cmd.latestMessage().getSenderId();
            String recvId = cmd.latestMessage().getRecvId();
            return ownerId.equals(senderId) ? recvId : senderId;
        }
        if (cmd.sessionType() == SessionType.NOTIFICATION.getCode()) {
            return cmd.latestMessage().getSenderId();
        }
        return cmd.latestMessage().getGroupId();
    }

    private Map<String, Object> buildUpdateFields(SetConversationRequest request) {
        Map<String, Object> fields = new HashMap<>();
        if (request.getRecvMsgOpt() != null) {
            fields.put("recvMsgOpt", request.getRecvMsgOpt());
        }
        if (request.getPinned() != null) {
            fields.put("pinned", request.getPinned());
        }
        if (request.getDraftText() != null) {
            fields.put("draftText", request.getDraftText());
        }
        if (request.getAttachedInfo() != null) {
            fields.put("attachedInfo", request.getAttachedInfo());
        }
        return fields;
    }

}
