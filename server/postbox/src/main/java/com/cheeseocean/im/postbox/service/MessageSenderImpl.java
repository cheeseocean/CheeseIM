package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.enums.ChatType;
import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.ErrorCode;
import com.cheeseocean.im.common.api.enums.GroupSendPermissionCode;
import com.cheeseocean.im.common.api.enums.ReceiveOption;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionRequest;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionResult;
import com.cheeseocean.im.common.api.permission.MessageSendPermissionService;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionDecision;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionRequest;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionResult;
import com.cheeseocean.im.common.api.permission.GroupMessageSendPermissionService;
import com.cheeseocean.im.common.api.rpc.MessageSender;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.common.core.store.idempotency.message.MessageSendInboxStore;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.common.core.util.IdGenerator;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class MessageSenderImpl implements MessageSender {

    private final IngressMessagePublisher ingressMessagePublisher;
    private final MessageSendInboxStore messageSendInboxStore;

    @DubboReference(check = false, retries = 0)
    private MessageSendPermissionService messageSendPermissionService;

    @DubboReference(check = false, retries = 0)
    private GroupMessageSendPermissionService groupMessageSendPermissionService;

    public MessageSenderImpl(IngressMessagePublisher ingressMessagePublisher,
                             MessageSendInboxStore messageSendInboxStore) {
        this.ingressMessagePublisher = ingressMessagePublisher;
        this.messageSendInboxStore = messageSendInboxStore;
    }

    /**
     * 统一构造发送响应，避免 accepted/基础字段在不同分支重复拼装。
     */
    private static SendMessageResp buildResp(Message msg,
                                             boolean accepted,
                                             long acceptedAt,
                                             ErrorCode errorCode) {
        SendMessageResp resp = new SendMessageResp();
        resp.setAccepted(accepted);
        ErrorCode stableError = accepted ? ErrorCode.SUCCESS : errorCode;
        resp.setErrorCode(stableError.getCode());
        resp.setErrorMessage(stableError.getDesc());
        if (accepted) {
            resp.setAcceptedAt(acceptedAt);
        }
        resp.setClientMsgId(msg.getClientMsgId());
        resp.setServerMsgId(msg.getServerMsgId());
        return resp;
    }


    @Override
    public SendMessageResp sendMessage(SendMessageReq req) {
        Message msg = req.getMsg();
        if (msg == null || msg.getContentType() == ContentType.TYPING) {
            // 输入中只能走 CHAT_TYPING 控制命令，禁止混入 ingress/history/seq 主链路。
            return rejectedResp(msg == null ? new Message() : msg, ErrorCode.INVALID_PARAM);
        }
        if (msg.getClientMsgId() == null || msg.getClientMsgId().isBlank()
                || msg.getSenderId() == null || msg.getSenderId().isBlank()) {
            return rejectedResp(msg, ErrorCode.INVALID_PARAM);
        }
        String conversationId = ConversationIdUtil.buildConversationId(
                msg.getChatType(), msg.getSenderId(), msg.getReceiverId(), msg.getGroupId());
        MessageOptions options = MessageOptionPolicy.fillDefaultOptions(msg);
        // 只归一化客户端静态选项；动态权限对 needOfflinePush 的改写不能改变重试身份。
        String payloadFingerprint = MessageSendFingerprint.payload(msg);
        String inboxKey = RedisKeys.messageSendInbox(MessageSendFingerprint.identity(
                msg.getSenderId(),
                conversationId,
                msg.getClientMsgId()));
        String ownerToken = IdGenerator.generateUUID();
        MessageSendInboxStore.Claim claim = messageSendInboxStore.claim(
                inboxKey,
                payloadFingerprint,
                IdGenerator.generateMsgId(),
                ownerToken,
                System.currentTimeMillis());

        if (claim.status() == MessageSendInboxStore.ClaimStatus.CONFLICT) {
            msg.setServerMsgId(null);
            return rejectedResp(msg, ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        msg.setServerMsgId(claim.serverMsgId());
        // 客户端时间不参与撤回窗口和历史排序真相；重试继续使用首次 claim 固定的服务端时间。
        msg.setSendTime(claim.createdAt());
        msg.setCreateTime(claim.createdAt());

        if (claim.status() == MessageSendInboxStore.ClaimStatus.ACCEPTED) {
            return buildResp(msg, true, claim.acceptedAt(), ErrorCode.SUCCESS);
        }
        if (claim.status() == MessageSendInboxStore.ClaimStatus.IN_PROGRESS) {
            return rejectedResp(msg, ErrorCode.MESSAGE_IN_PROGRESS);
        }

        Boolean effectiveOfflinePush = claim.effectiveOfflinePush();
        // 首次允许结果会固定有效推送策略；租约恢复复用该结果，不让动态配置变化改写同一消息。
        if (effectiveOfflinePush == null) {
            try {
                SendMessageResp blocked = checkMessagePermission(msg, conversationId, options);
                if (blocked != null) {
                    messageSendInboxStore.release(inboxKey, ownerToken);
                    msg.setServerMsgId(null);
                    blocked.setServerMsgId(null);
                    return blocked;
                }
            } catch (RuntimeException exception) {
                releaseAfterFailure(inboxKey, ownerToken, exception);
                throw exception;
            }
        }
        if (effectiveOfflinePush == null) {
            try {
                effectiveOfflinePush = messageSendInboxStore.bindEffectiveOfflinePush(
                        inboxKey,
                        ownerToken,
                        Boolean.TRUE.equals(options.getNeedOfflinePush()));
            } catch (RuntimeException exception) {
                releaseAfterFailure(inboxKey, ownerToken, exception);
                throw exception;
            }
        }
        options.setNeedOfflinePush(effectiveOfflinePush);

        try {
            ingressMessagePublisher.publish(msg);
        } catch (RuntimeException exception) {
            releaseAfterFailure(inboxKey, ownerToken, exception);
            throw exception;
        }

        // 消息进入入口队列并取得 broker ACK 后才标记 accepted。
        long acceptedAt = messageSendInboxStore.markAccepted(
                inboxKey,
                payloadFingerprint,
                claim.serverMsgId(),
                System.currentTimeMillis());
        return buildResp(msg, true, acceptedAt, ErrorCode.SUCCESS);
    }

    private SendMessageResp checkMessagePermission(Message message,
                                                   String conversationId,
                                                   MessageOptions options) {
        if (message.getChatType() == ChatType.GROUP) {
            return checkGroupChatPermission(message);
        }
        if (requiresPermissionCheck(message, options)) {
            return checkSingleChatPermission(message, conversationId, options);
        }
        return null;
    }

    private SendMessageResp checkGroupChatPermission(Message message) {
        if (message.getGroupId() == null || message.getGroupId().isBlank()) {
            return rejectedResp(message, ErrorCode.INVALID_PARAM);
        }
        GroupMessageSendPermissionRequest request = new GroupMessageSendPermissionRequest();
        request.setGroupId(message.getGroupId());
        request.setSenderIds(java.util.List.of(message.getSenderId()));
        GroupMessageSendPermissionResult result = groupMessageSendPermissionService.check(request);
        GroupMessageSendPermissionDecision decision =
                result == null ? null : result.decisionFor(message.getSenderId());
        if (result == null || decision == null) {
            return rejectedResp(message, ErrorCode.INTERNAL_ERROR);
        }
        if (!decision.isAllowed()) {
            return rejectedResp(message, groupPermissionError(decision.permission()));
        }
        if (result.getGroupType() == null) {
            return rejectedResp(message, ErrorCode.INTERNAL_ERROR);
        }
        return null;
    }

    private ErrorCode groupPermissionError(GroupSendPermissionCode permission) {
        return switch (permission) {
            case GROUP_NOT_FOUND -> ErrorCode.GROUP_NOT_FOUND;
            case GROUP_DISBANDED, GROUP_BANNED -> ErrorCode.GROUP_UNAVAILABLE;
            case NOT_MEMBER -> ErrorCode.GROUP_NOT_MEMBER;
            case MEMBER_MUTED -> ErrorCode.GROUP_MEMBER_MUTED;
            case INVALID_REQUEST -> ErrorCode.INVALID_PARAM;
            case ALLOWED -> ErrorCode.SUCCESS;
        };
    }

    private void releaseAfterFailure(String inboxKey,
                                     String ownerToken,
                                     RuntimeException originalFailure) {
        try {
            messageSendInboxStore.release(inboxKey, ownerToken);
        } catch (RuntimeException releaseFailure) {
            // 释放失败时租约仍会自动过期；保留原始故障，避免基础设施清理异常掩盖业务异常。
            originalFailure.addSuppressed(releaseFailure);
        }
    }

    /**
     * 单聊接收方权限仅对非通知消息生效；群消息由独立成员/群状态权限检查处理。
     */
    private boolean requiresPermissionCheck(Message msg, MessageOptions options) {
        if (options.getNotification() != null && options.getNotification()) {
            return false;
        }
        return msg.getChatType() == ChatType.PRIVATE;
    }

    /**
     * 单聊消息发送权限校验。
     * <p>
     * 发送热路径只向 business 发起一次聚合权限查询，避免黑名单、用户接收配置、
     * 会话接收配置三段同步 Dubbo 放大 RTT。
     *
     * <p>聚合结果语义：
     * 1. 黑名单：recvId 将 senderId 加入黑名单则直接拒绝。
     * 2. 用户接收配置：
     * BLOCK (1)        → 直接拒绝
     * DO_NOT_DISTURB (2) → 关闭离线推送，继续投递
     * 3. 会话级接收配置：
     * BLOCK (1)        → 直接拒绝（已读回执除外）
     * DO_NOT_DISTURB (2) → 关闭离线推送，继续投递
     * <p>
     * 返回 null 表示消息可正常投递（options 可能已被修改）；
     * 返回非 null 表示消息需被丢弃。
     */
    private SendMessageResp checkSingleChatPermission(Message req,
                                                      String conversationId,
                                                      MessageOptions options) {
        String senderId = req.getSenderId();
        String receiverId   = req.getReceiverId();

        MessageSendPermissionRequest request = new MessageSendPermissionRequest();
        request.setSenderId(senderId);
        request.setReceiverId(receiverId);
        request.setConversationId(conversationId);
        MessageSendPermissionResult permission = messageSendPermissionService.check(request);

        if (permission == null || permission.isBlockedByReceiver()) {
            return rejectedResp(req);
        }

        // 2. 用户级别接收配置
        ReceiveOption globalOpt = ReceiveOption.fromCode(permission.getGlobalReceiveOption());
        if (globalOpt == ReceiveOption.BLOCK) {
            return rejectedResp(req);
        }
        if (globalOpt == ReceiveOption.DO_NOT_DISTURB) {
            options.setNeedOfflinePush(false);
        }

        // 3. 会话级接收配置
        ReceiveOption convOpt = ReceiveOption.fromCode(permission.getConversationReceiveOption());
        if (convOpt == ReceiveOption.BLOCK) {
            // 已读回执绕过会话级屏蔽
            if (!MessageOptionPolicy.isReadReceipt(req.getContentType())) {
                return rejectedResp(req);
            }
        }
        if (convOpt == ReceiveOption.DO_NOT_DISTURB) {
            options.setNeedOfflinePush(false);
        }

        return null;
    }

    private static SendMessageResp rejectedResp(Message msg) {
        return rejectedResp(msg, ErrorCode.MSG_SEND_FAILED);
    }

    private static SendMessageResp rejectedResp(Message msg, ErrorCode errorCode) {
        return buildResp(msg, false, 0L, errorCode);
    }
}
