package com.cheeseocean.im.business.service.conversation;

import com.cheeseocean.im.common.api.conversation.ConversationWriteService;
import com.cheeseocean.im.common.api.dto.conversation.SetConversationRequest;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 会话写入服务实现。
 *
 * <p>负责写扩散的会话创建（单聊/群聊）和用户配置更新（置顶/免打扰等）。
 * 所有创建操作均幂等，可安全重试。
 */
@Service
@DubboService
public class ConversationWriteServiceImpl implements ConversationWriteService {

    private final ConversationLifecycleService conversationLifecycleService;

    public ConversationWriteServiceImpl(ConversationLifecycleService conversationLifecycleService) {
        this.conversationLifecycleService = conversationLifecycleService;
    }

    @Override
    public void createSingleChatConversation(String senderId, String recvId,
                                             String conversationId, int conversationType) {
        conversationLifecycleService.createSingleChatConversation(senderId, recvId, conversationId, conversationType);
    }

    @Override
    public void createGroupChatConversations(String groupId, String conversationId, List<String> userIds) {
        conversationLifecycleService.createGroupChatConversations(groupId, conversationId, userIds);
    }

    @Override
    public void setConversations(List<String> userIds, SetConversationRequest request) {
        conversationLifecycleService.setConversations(userIds, request);
    }
}
