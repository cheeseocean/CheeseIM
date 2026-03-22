package com.cheeseocean.im.postoffice.service;

import com.cheeseocean.im.common.api.dto.message.MessageOptions;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.dto.message.SendMessageReq;
import com.cheeseocean.im.postoffice.connection.ConnectionContext;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MessageSendReqMapper {

    public SendMessageReq map(Message message, UserConnection connection, String requestId) {
        ConnectionContext context = connection.getContext();
        String senderId = context != null && context.getUserId() != null ? context.getUserId() : connection.getUserID();

        SendMessageReq req = new SendMessageReq();
        req.setRequestId(requestId);
        req.setSenderId(senderId);
        req.setSessionType(message.getSessionType());
        req.setRecvId(message.getRecvID());
        req.setGroupId(message.getGroupID());
        req.setClientMsgId(message.getClientMsgID());
        req.setContentType(message.getContentType());
        req.setContent(message.getContent());
        req.setSendTime(message.getSendTime());
        req.setOptions(mapOptions(message.getOptions()));
        req.setExt(mapExt(message));
        return req;
    }

    private Map<String, String> mapExt(Message message) {
        Map<String, String> ext = new HashMap<>();
        if (message.getAttachedInfo() != null && !message.getAttachedInfo().isBlank()) {
            ext.put("attachedInfo", message.getAttachedInfo());
        }
        return ext;
    }

    private MessageOptions mapOptions(Map<String, Boolean> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        MessageOptions options = new MessageOptions();
        options.setNeedHistory(source.get("needHistory"));
        options.setNeedConversation(source.get("needConversation"));
        options.setNeedUnreadCount(source.get("needUnreadCount"));
        options.setNeedOnlinePush(source.get("needOnlinePush"));
        options.setNeedOfflinePush(source.get("needOfflinePush"));
        options.setSenderSync(source.get("senderSync"));
        options.setNotification(source.get("notification"));
        options.setNeedLastMessage(source.get("needLastMessage"));
        return options;
    }
}
