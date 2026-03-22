package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.common.api.dto.message.SendMessageResp;
import com.cheeseocean.im.common.api.event.IngressEvent;
import com.cheeseocean.im.common.api.rpc.MessageSendRpc;
import com.cheeseocean.im.common.core.enums.SessionType;
import com.cheeseocean.im.common.core.util.ConversationIdUtil;
import com.cheeseocean.im.common.utils.IdGenerator;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
@DubboService
public class MessageSendRpcImpl implements MessageSendRpc {

    private final IngressEventPublisher ingressEventPublisher;

    public MessageSendRpcImpl(IngressEventPublisher ingressEventPublisher) {
        this.ingressEventPublisher = ingressEventPublisher;
    }

    @Override
    public SendMessageResp sendMessage(SendMessageReq req) {
        String conversationId = resolveConversationId(req);
        String serverMsgId = IdGenerator.generateMsgId();
        MessageOptions options = fillDefaultOptions(req.getOptions());

        IngressEvent event = new IngressEvent();
        event.setRequestId(req.getRequestId());
        event.setConversationId(conversationId);
        event.setClientMsgId(req.getClientMsgId());
        event.setServerMsgId(serverMsgId);
        event.setSenderId(req.getSenderId());
        event.setRecvId(req.getRecvId());
        event.setGroupId(req.getGroupId());
        event.setSessionType(req.getSessionType());
        event.setContentType(req.getContentType());
        event.setContent(req.getContent());
        event.setSendTime(req.getSendTime() == null ? System.currentTimeMillis() : req.getSendTime());
        event.setOptions(options);
        event.setExt(req.getExt());
        ingressEventPublisher.publish(event);

        SendMessageResp resp = new SendMessageResp();
        resp.setAccepted(true);
        resp.setRequestId(req.getRequestId());
        resp.setConversationId(conversationId);
        resp.setClientMsgId(req.getClientMsgId());
        resp.setServerMsgId(serverMsgId);
        return resp;
    }

    private static String resolveConversationId(SendMessageReq req) {
        if (req.getSessionType() != null && req.getSessionType() == SessionType.GROUP) {
            return ConversationIdUtil.group(req.getGroupId());
        }
        if (req.getSessionType() != null && req.getSessionType() == SessionType.NOTIFICATION) {
            return ConversationIdUtil.notification(req.getRecvId());
        }
        return ConversationIdUtil.single(req.getSenderId(), req.getRecvId());
    }

    private static MessageOptions fillDefaultOptions(MessageOptions options) {
        if (options != null) {
            return options;
        }
        MessageOptions defaults = new MessageOptions();
        defaults.setNeedHistory(true);
        defaults.setNeedConversation(true);
        defaults.setNeedUnreadCount(true);
        defaults.setNeedOnlinePush(true);
        defaults.setNeedOfflinePush(true);
        defaults.setSenderSync(true);
        defaults.setNotification(false);
        defaults.setNeedLastMessage(true);
        return defaults;
    }
}
