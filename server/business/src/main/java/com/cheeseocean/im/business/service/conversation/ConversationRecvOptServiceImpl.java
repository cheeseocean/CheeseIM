package com.cheeseocean.im.business.service.conversation;

import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.cheeseocean.im.common.api.conversation.ConversationRecvOptService;
import com.cheeseocean.im.common.core.business.repository.UserConversationRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 会话消息接收选项服务实现。
 *
 * <p>提供单个会话级别的免打扰开关读写能力，
 * 变更后通过 {@link ConversationSettingsNotifier} 推送通知（如更新推送服务缓存）。
 */
@Service
@DubboService
public class ConversationRecvOptServiceImpl implements ConversationRecvOptService {

    private final UserConversationRepository stateRepository;
    private final ConversationSettingsNotifier conversationSettingsNotifier;

    public ConversationRecvOptServiceImpl(UserConversationRepository stateRepository,
                                          ConversationSettingsNotifier conversationSettingsNotifier) {
        this.stateRepository = stateRepository;
        this.conversationSettingsNotifier = conversationSettingsNotifier;
    }

    @Override
    @Cached(name = "business:conversation:recvOpt:", key = "#ownerUserId + ':' + #conversationId", expire = 300, cacheType = CacheType.BOTH)
    public int getRecvMsgOpt(String ownerUserId, String conversationId) {
        return stateRepository.getRecvMsgOpt(ownerUserId, conversationId);
    }

    @Override
    @CacheInvalidate(name = "business:conversation:recvOpt:", key = "#ownerUserId + ':' + #conversationId")
    public void setRecvMsgOpt(String ownerUserId, String conversationId, int recvMsgOpt) {
        stateRepository.setRecvMsgOpt(ownerUserId, conversationId, recvMsgOpt);
        conversationSettingsNotifier.notifyRecvMsgOptChanged(ownerUserId, conversationId, recvMsgOpt);
    }
}
