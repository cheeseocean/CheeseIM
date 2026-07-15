package com.cheeseocean.im.apiserver.facade;

import com.cheeseocean.im.apiserver.model.request.BatchGetConversationsRequest;
import com.cheeseocean.im.apiserver.model.request.PullMessagesRequest;
import com.cheeseocean.im.apiserver.model.request.RevokeMessageRequest;
import com.cheeseocean.im.apiserver.model.request.SeqRangeItemRequest;
import com.cheeseocean.im.apiserver.model.request.GetConversationRequest;
import com.cheeseocean.im.apiserver.model.request.ListConversationMessagesRequest;
import com.cheeseocean.im.apiserver.model.request.ListConversationsRequest;
import com.cheeseocean.im.apiserver.model.request.SetConversationsRequest;
import com.cheeseocean.im.apiserver.model.request.AckReadSeqRequest;
import com.cheeseocean.im.apiserver.model.response.ConversationIdsHashResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationIdsResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationIncrementalSyncResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationControlEventResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationControlEventSyncResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationMaxSeqResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationReadSnapshotResponse;
import com.cheeseocean.im.apiserver.model.response.ConversationResponse;
import com.cheeseocean.im.apiserver.model.response.HistoryMessageResponse;
import com.cheeseocean.im.apiserver.model.response.MessageMutationResponse;
import com.cheeseocean.im.apiserver.model.response.MessageMutationSyncResponse;
import com.cheeseocean.im.apiserver.model.response.PullMessagesResponse;
import com.cheeseocean.im.apiserver.model.response.PulledConversationMessagesResponse;
import com.cheeseocean.im.apiserver.model.response.SyncMessageResponse;
import com.cheeseocean.im.common.api.business.domain.User;
import com.cheeseocean.im.common.api.business.domain.ConversationControlEvent;
import com.cheeseocean.im.common.api.business.domain.UserConversation;
import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.conversation.ConversationSyncService;
import com.cheeseocean.im.common.api.conversation.ReadStateService;
import com.cheeseocean.im.common.api.dto.conversation.ConversationIncrementalSyncResult;
import com.cheeseocean.im.common.api.dto.conversation.ConversationReadSnapshot;
import com.cheeseocean.im.common.api.dto.conversation.PullMessages;
import com.cheeseocean.im.common.api.dto.conversation.SeqRangeRequest;
import com.cheeseocean.im.common.api.conversation.ConversationControlEventQueryService;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageMutationResult;
import com.cheeseocean.im.common.api.dto.message.MessageMutationSyncResult;
import com.cheeseocean.im.common.api.enums.ConversationKind;
import com.cheeseocean.im.common.api.permission.ConversationPermissionRequest;
import com.cheeseocean.im.common.api.permission.ConversationPermissionDubboService;
import com.cheeseocean.im.common.api.permission.PermissionCheckResult;
import com.cheeseocean.im.common.api.message.MessageMutationService;
import com.cheeseocean.im.common.api.message.MessageHistoryQueryService;
import com.cheeseocean.im.common.api.session.SessionPrincipal;
import com.cheeseocean.im.common.api.user.UserInfoService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 会话 HTTP facade，负责处理登录态解析和 HTTP 查询编排。
 */
@Service
public class ConversationFacade {

    private final ConversationService conversationService;
    private final ConversationSyncService conversationSyncService;
    private final ReadStateService readStateService;
    @DubboReference(check = false)
    private ConversationPermissionDubboService permissionService;
    @DubboReference(check = false)
    private ConversationControlEventQueryService controlEventQueryService;

    @DubboReference(check = false)
    private MessageHistoryQueryService messageHistoryQueryService;

    @DubboReference(check = false)
    private UserInfoService userInfoService;

    @DubboReference(check = false)
    private MessageMutationService messageMutationService;

    public ConversationFacade(ConversationService conversationService,
                              ConversationSyncService conversationSyncService,
                              ReadStateService readStateService,
                              ConversationPermissionDubboService permissionService,
                              MessageHistoryQueryService messageHistoryQueryService) {
        this(conversationService, conversationSyncService, readStateService, permissionService);
        this.messageHistoryQueryService = messageHistoryQueryService;
    }

    public ConversationFacade(ConversationService conversationService,
                              ConversationSyncService conversationSyncService,
                              ReadStateService readStateService,
                              ConversationPermissionDubboService permissionService) {
        this.conversationService = conversationService;
        this.conversationSyncService = conversationSyncService;
        this.readStateService = readStateService;
        this.permissionService = permissionService;
    }

    public List<ConversationResponse> listConversations(SessionPrincipal session, ListConversationsRequest request) {
        int effectiveLimit = Math.max(1, request.getLimit());
        return safeConversations(conversationService.getAllConversations(session.getUserId())).stream()
                .filter(conversation -> conversation != null && conversation.getConversationId() != null)
                .filter(conversation -> allow(session, conversation.getConversationId()))
                .sorted(Comparator.comparingLong(ConversationFacade::sortTime).reversed())
                .limit(effectiveLimit)
                .map(this::toConversationResponse)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<ConversationResponse> getAllConversations(SessionPrincipal session) {
        return safeConversations(conversationService.getAllConversations(session.getUserId())).stream()
                .map(this::toConversationResponse)
                .toList();
    }

    public ConversationResponse getConversation(SessionPrincipal session, GetConversationRequest request) {
        return toConversationResponse(conversationService.getConversation(session.getUserId(), request.getConversationId()));
    }

    public List<ConversationResponse> getConversations(SessionPrincipal session, BatchGetConversationsRequest request) {
        return conversationService.getConversations(session.getUserId(), request.getConversationIds()).stream()
                .map(this::toConversationResponse)
                .toList();
    }

    public ConversationIdsResponse getConversationIds(SessionPrincipal session) {
        ConversationIdsResponse response = new ConversationIdsResponse();
        response.setConversationIds(conversationService.getConversationIds(session.getUserId()));
        return response;
    }

    public ConversationIdsHashResponse getConversationIdsHash(SessionPrincipal session) {
        ConversationIdsHashResponse response = new ConversationIdsHashResponse();
        response.setHash(conversationService.getConversationIdsHash(session.getUserId()));
        return response;
    }

    public ConversationIncrementalSyncResponse syncConversations(SessionPrincipal session,
                                                                 String versionId,
                                                                 Long version,
                                                                 Long idHash) {
        ConversationIncrementalSyncResult result = conversationService.syncConversations(
                session.getUserId(),
                versionId,
                version == null ? 0L : version,
                idHash == null ? 0L : idHash
        );
        ConversationIncrementalSyncResponse response = new ConversationIncrementalSyncResponse();
        response.setVersionId(result.getVersionId());
        response.setVersion(result.getVersion());
        response.setIdHash(result.getIdHash());
        response.setFull(result.isFull());
        response.setInsert(result.getInsert().stream().map(this::toConversationResponse).filter(Objects::nonNull).toList());
        response.setUpdate(result.getUpdate().stream().map(this::toConversationResponse).filter(Objects::nonNull).toList());
        response.setDelete(result.getDelete());
        response.setReadStateChangedConversationIds(result.getReadStateChangedConversationIds());
        return response;
    }

    public ConversationIdsResponse getNotNotifyConversationIds(SessionPrincipal session) {
        ConversationIdsResponse response = new ConversationIdsResponse();
        response.setConversationIds(conversationService.getNotNotifyConversationIds(session.getUserId()));
        return response;
    }

    public ConversationIdsResponse getPinnedConversationIds(SessionPrincipal session) {
        ConversationIdsResponse response = new ConversationIdsResponse();
        response.setConversationIds(conversationService.getPinnedConversationIds(session.getUserId()));
        return response;
    }

    public void setConversations(SessionPrincipal session, SetConversationsRequest request) {
        List<String> ownerUserIds = new ArrayList<>(1);
        ownerUserIds.add(session.getUserId());
        conversationService.setConversations(ownerUserIds, request.getPayload());
    }

    public void deleteConversation(SessionPrincipal session, String conversationId) {
        conversationService.deleteConversation(session.getUserId(), conversationId);
    }

    public List<ConversationMaxSeqResponse> getConversationMaxSeqs(SessionPrincipal session, List<String> conversationIds) {
        return conversationSyncService.getConversationMaxSeqs(session.getUserId(), conversationIds).entrySet().stream()
                .map(entry -> {
                    ConversationMaxSeqResponse response = new ConversationMaxSeqResponse();
                    response.setConversationId(entry.getKey());
                    response.setMaxSeq(entry.getValue());
                    return response;
                })
                .toList();
    }

    public PullMessagesResponse pullMessages(SessionPrincipal session,
                                                                                         PullMessagesRequest request) {
        List<SeqRangeRequest> ranges = request.getRanges().stream()
                .map(this::toSeqRangeRequest)
                .toList();
        PullMessages pulled =
                conversationSyncService.pullMessagesBySeqRanges(
                        session.getUserId(),
                        ranges,
                        request.getLimitPerConversation()
                );
        PullMessagesResponse response = new PullMessagesResponse();
        for (Map.Entry<String, List<Message>> entry : pulled.getMessagesByConversation().entrySet()) {
            String conversationId = entry.getKey();
            PulledConversationMessagesResponse item = new PulledConversationMessagesResponse();
            item.setConversationId(conversationId);
            item.setEndSeq(pulled.getEndSeqByConversation().getOrDefault(conversationId, 0L));
            item.setCompleted(Boolean.TRUE.equals(pulled.getCompletedByConversation().get(conversationId)));
            item.setMessages(entry.getValue().stream().map(this::toSyncMessageResponse).toList());
            response.getConversations().add(item);
        }
        return response;
    }

    public List<ConversationReadSnapshotResponse> getConversationReadSnapshots(SessionPrincipal session,
                                                                               List<String> conversationIds) {
        return conversationSyncService.getConversationReadSnapshots(session.getUserId(), conversationIds)
                .values()
                .stream()
                .map(this::toConversationReadSnapshotResponse)
                .toList();
    }

    public void ackReadSeq(SessionPrincipal session, String conversationId, AckReadSeqRequest request) {
        readStateService.acknowledge(session.getUserId(), conversationId, request.getReadSeq());
    }

    public MessageMutationResponse revokeMessage(SessionPrincipal session,
                                                 String conversationId,
                                                 RevokeMessageRequest request) {
        MessageMutationResult result = messageMutationService.revoke(
                session.getUserId(), conversationId, request.getServerMsgId(), request.getReason());
        if (result == null || !result.isSuccess()) {
            throw new IllegalStateException(result == null ? "撤回失败" : result.getErrorMessage());
        }
        return toMutationResponse(result);
    }

    public List<HistoryMessageResponse> getConversationMessages(SessionPrincipal session, ListConversationMessagesRequest request) {
        return messageHistoryQueryService.getConversationMessages(session, request.getConversationId(), request.getLimit()).stream()
                .map(message -> {
                    HistoryMessageResponse response = new HistoryMessageResponse();
                    response.setSequence(message.getSequence());
                    response.setServerMsgId(message.getServerMsgId());
                    response.setSenderId(message.getSenderId());
                    response.setSenderName(message.getSenderName());
                    response.setContent(message.getContent());
                    response.setPreviewType(message.getPreviewType());
                    response.setSendTime(message.getSendTime());
                    response.setRevoked(message.isRevoked());
                    response.setRevokeOperatorUserId(message.getRevokeOperatorUserId());
                    response.setRevokeOperatorName(message.getRevokeOperatorName());
                    response.setRevokedAt(message.getRevokedAt());
                    response.setMutationVersion(message.getMutationVersion());
                    return response;
                })
                .toList();
    }

    public MessageMutationSyncResponse syncMessageMutations(SessionPrincipal session,
                                                            String conversationId,
                                                            long afterCreatedAt,
                                                            String afterMutationId,
                                                            int limit) {
        MessageMutationSyncResult result = messageMutationService.sync(
                session.getUserId(), conversationId, afterCreatedAt, afterMutationId, limit);
        if (result == null || !result.isSuccess()) {
            throw new IllegalStateException(result == null ? "mutation 同步失败" : result.getErrorMessage());
        }
        MessageMutationSyncResponse response = new MessageMutationSyncResponse();
        response.setMutations(result.getMutations().stream().map(this::toMutationResponse).toList());
        response.setNextCreatedAt(result.getNextCreatedAt());
        response.setNextMutationId(result.getNextMutationId());
        response.setHasMore(result.isHasMore());
        return response;
    }

    public ConversationControlEventSyncResponse syncControlEvents(SessionPrincipal session,
                                                                   long cursor,
                                                                   int limit) {
        if (controlEventQueryService == null) {
            throw new IllegalStateException("控制事件同步暂不可用");
        }
        int pageSize = Math.max(1, Math.min(limit, 200));
        List<ConversationControlEvent> events = controlEventQueryService.findAfter(
                session.getUserId(), Math.max(0L, cursor), pageSize);
        ConversationControlEventSyncResponse response = new ConversationControlEventSyncResponse();
        response.setEvents(events.stream().map(this::toControlEventResponse).toList());
        response.setNextCursor(events.isEmpty() ? Math.max(0L, cursor) : events.get(events.size() - 1).getCursor());
        response.setHasMore(events.size() == pageSize);
        return response;
    }

    private MessageMutationResponse toMutationResponse(MessageMutationResult mutation) {
        MessageMutationResponse response = new MessageMutationResponse();
        response.setMutationId(mutation.getMutationId());
        response.setConversationId(mutation.getConversationId());
        response.setServerMsgId(mutation.getServerMsgId());
        response.setOperatorUserId(mutation.getOperatorUserId());
        response.setOperatorName(mutation.getOperatorName());
        response.setTargetSenderId(mutation.getTargetSenderId());
        response.setTargetSenderName(mutation.getTargetSenderName());
        response.setRevokedAt(mutation.getRevokedAt());
        response.setMutationVersion(mutation.getMutationVersion());
        response.setReason(mutation.getReason());
        return response;
    }

    private ConversationControlEventResponse toControlEventResponse(ConversationControlEvent event) {
        ConversationControlEventResponse response = new ConversationControlEventResponse();
        response.setEventId(event.getEventId());
        response.setCursor(event.getCursor());
        response.setConversationId(event.getConversationId());
        response.setType(event.getType().getCode());
        response.setPayload(event.getPayload());
        response.setCreatedAt(event.getCreatedAt());
        response.setExpiresAt(event.getExpiresAt());
        return response;
    }

    private ConversationResponse toConversationResponse(UserConversation conversation) {
        if (conversation == null) {
            return null;
        }
        ConversationResponse response = new ConversationResponse();
        response.setOwnerUserId(conversation.getOwnerUserId());
        response.setConversationId(conversation.getConversationId());
        response.setConversationType(conversation.getChatType());
        response.setTargetId(conversation.getTargetId());
        response.setReceiveOpt(conversation.getReceiveOpt());
        response.setUnreadCount(conversation.getUnreadCount());
        response.setPinned(conversation.isPinned());
        response.setAttachedInfo(conversation.getAttachedInfo());
        response.setGroupAtType(conversation.getGroupAtType());
        response.setAutoCleanup(conversation.isAutoCleanup());
        response.setCleanupCycle(conversation.getCleanupCycle());
        response.setLatestCleanupTime(conversation.getLatestCleanupTime());
        response.setCreatedAt(conversation.getCreatedAt());
        response.setUpdatedAt(conversation.getUpdatedAt());
        response.setKind(resolveKind(conversation));
        response.setTitle(resolveTitle(conversation));
        response.setSubtitle(resolveSubtitle(conversation));
        response.setNotification(resolveKind(conversation) == ConversationKind.NOTIFICATION);
        return response;
    }

    private List<UserConversation> safeConversations(List<UserConversation> conversations) {
        return conversations == null ? new ArrayList<>() : conversations;
    }

    private ConversationKind resolveKind(UserConversation conversation) {
        return switch (conversation.getChatType()) {
            case 2 -> ConversationKind.GROUP;
            case 3 -> ConversationKind.NOTIFICATION;
            default -> ConversationKind.DIRECT;
        };
    }

    private String resolveTitle(UserConversation conversation) {
        return switch (resolveKind(conversation)) {
            case GROUP -> defaultValue(conversation.getTargetId(), conversation.getConversationId());
            case NOTIFICATION -> "System notifications";
            case CHANNEL -> defaultValue(conversation.getTargetId(), conversation.getConversationId());
            case DIRECT -> resolveDirectTitle(conversation.getTargetId(), conversation.getConversationId());
        };
    }

    private String resolveSubtitle(UserConversation conversation) {
        return switch (resolveKind(conversation)) {
            case GROUP -> "Group conversation";
            case NOTIFICATION -> "Notification conversation";
            case CHANNEL -> "Channel conversation";
            case DIRECT -> "Direct conversation";
        };
    }

    private String resolveDirectTitle(String targetId, String fallback) {
        String userId = defaultValue(targetId, fallback);
        if (userId == null || userId.isBlank() || userInfoService == null) {
            return userId;
        }
        try {
            User user = userInfoService.getUserInfo(userId);
            if (user != null && user.getNickname() != null && !user.getNickname().isBlank()) {
                return user.getNickname();
            }
        } catch (Exception ignored) {
            // 用户资料服务异常时降级展示 targetId，避免会话列表整体失败。
        }
        return userId;
    }

    private SeqRangeRequest toSeqRangeRequest(SeqRangeItemRequest request) {
        SeqRangeRequest range = new SeqRangeRequest();
        range.setConversationId(request.getConversationId());
        range.setBeginSeq(request.getBeginSeq());
        range.setEndSeq(request.getEndSeq());
        return range;
    }

    private ConversationReadSnapshotResponse toConversationReadSnapshotResponse(ConversationReadSnapshot snapshot) {
        ConversationReadSnapshotResponse response = new ConversationReadSnapshotResponse();
        response.setConversationId(snapshot.getConversationId());
        response.setReadSeq(snapshot.getReadSeq());
        response.setMaxSeq(snapshot.getMaxSeq());
        response.setUnreadCount(snapshot.getUnreadCount());
        return response;
    }

    private SyncMessageResponse toSyncMessageResponse(Message message) {
        SyncMessageResponse response = new SyncMessageResponse();
        response.setSeq(message.getSeq());
        response.setClientMsgId(message.getClientMsgId());
        response.setServerMsgId(message.getServerMsgId());
        response.setSenderId(message.getSenderId());
        response.setSenderNickName(message.getSenderNickName());
        response.setReceiverId(message.getReceiverId());
        response.setGroupId(message.getGroupId());
        response.setContentType(message.getContentType() == null ? null : message.getContentType().getCode());
        response.setSessionType(message.getChatType() == null ? null : message.getChatType().getCode());
        response.setContent(message.getContent());
        response.setSendTime(message.getSendTime());
        response.setCreateTime(message.getCreateTime());
        response.setStatus(message.getStatus() == null ? null : message.getStatus().getCode());
        response.setPlatformType(message.getPlatformType() == null ? null : message.getPlatformType().getCode());
        response.setUniqueId(message.getUniqueId());
        response.setSource(message.getSource() == null ? null : message.getSource().getCode());
        response.setAttributes(message.getAttributes());
        return response;
    }

    private String defaultValue(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null ? "" : fallback;
    }

    private boolean allow(SessionPrincipal session, String conversationId) {
        if (permissionService == null) {
            return true;
        }
        ConversationPermissionRequest request = new ConversationPermissionRequest();
        request.setTenantId(session.getTenantId());
        request.setUserId(session.getUserId());
        request.setConversationId(conversationId);
        PermissionCheckResult result = permissionService.check(request);
        return result == null || result.isAllowed();
    }

    private static long sortTime(UserConversation conversation) {
        if (conversation == null) {
            return Long.MIN_VALUE;
        }
        return conversation.getUpdatedAt();
    }
}
