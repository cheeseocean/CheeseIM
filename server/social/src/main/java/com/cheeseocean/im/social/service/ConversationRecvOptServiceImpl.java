package com.cheeseocean.im.social.service;

import com.cheeseocean.im.common.api.conversation.ConversationRecvOptService;
import com.cheeseocean.im.social.repository.ConversationStore;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class ConversationRecvOptServiceImpl implements ConversationRecvOptService {

    private final ConversationStore conversationStore;
    private final ConversationSettingsNotifier conversationSettingsNotifier;

    public ConversationRecvOptServiceImpl(ConversationStore conversationStore,
                                          ConversationSettingsNotifier conversationSettingsNotifier) {
        this.conversationStore = conversationStore;
        this.conversationSettingsNotifier = conversationSettingsNotifier;
    }

    @Override
    public int getRecvMsgOpt(String ownerUserId, String conversationId) {
        return conversationStore.getRecvMsgOpt(ownerUserId, conversationId);
    }

    @Override
    public void setRecvMsgOpt(String ownerUserId, String conversationId, int recvMsgOpt) {
        conversationStore.setRecvMsgOpt(ownerUserId, conversationId, recvMsgOpt);
        conversationSettingsNotifier.notifyRecvMsgOptChanged(ownerUserId, conversationId, recvMsgOpt);
    }
}
