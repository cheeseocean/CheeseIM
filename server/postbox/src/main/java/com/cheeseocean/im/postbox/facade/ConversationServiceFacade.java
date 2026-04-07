package com.cheeseocean.im.postbox.facade;

import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.cheeseocean.im.common.api.conversation.ConversationQueryService;
import com.cheeseocean.im.common.api.dto.conversation.ConversationDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author xxxcrel
 * @date 2026/4/3 13:51
 */
@Service
public class ConversationServiceFacade {

    @DubboReference
    private ConversationQueryService conversationQueryService;

    /** 透传单会话查询。 */
    public ConversationDTO getConversation(String ownerUserId, String conversationId) {
        return conversationQueryService.getConversation(ownerUserId, conversationId);
    }

    /** 透传批量会话查询。 */
    public List<ConversationDTO> getConversations(String ownerUserId, List<String> conversationIds) {
        return conversationQueryService.getConversations(ownerUserId, conversationIds);
    }

    /** 透传全量会话查询。 */
    public List<ConversationDTO> getAllConversations(String ownerUserId) {
        return conversationQueryService.getAllConversations(ownerUserId);
    }

    /** 透传会话 ID 查询。 */
    public List<String> getConversationIds(String ownerUserId) {
        return conversationQueryService.getConversationIds(ownerUserId);
    }

    /** 透传会话 ID 哈希查询。 */
    public long getConversationIdsHash(String ownerUserId) {
        return conversationQueryService.getConversationIdsHash(ownerUserId);
    }

    /** 对会话级接收选项做 facade 级缓存，避免消息链路重复远程查询。 */
    @Cached(name = "conversation:receive_options:", key = "#ownerUserId + ':' + #conversationId", expire = 300, cacheType = CacheType.REMOTE)
    public int getReceiveOption(String ownerUserId, String conversationId) {
        return conversationQueryService.getReceiveOption(ownerUserId, conversationId);
    }

    /** 透传会话级接收选项更新。 */
    public void setReceiveOption(String ownerUserId, String conversationId, int recvMsgOpt) {
        conversationQueryService.setReceiveOption(ownerUserId, conversationId, recvMsgOpt);
    }

    /** 透传离线推送用户过滤。 */
    public List<String> getOfflinePushUserIds(String conversationId, List<String> candidateUserIds) {
        return conversationQueryService.getOfflinePushUserIds(conversationId, candidateUserIds);
    }
}
