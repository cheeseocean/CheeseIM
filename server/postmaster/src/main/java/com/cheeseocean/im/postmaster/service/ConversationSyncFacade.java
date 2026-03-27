package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.conversation.ConversationSyncCommand;
import com.cheeseocean.im.common.api.conversation.ConversationSyncService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * {@link ConversationSyncService} 的 Dubbo 客户端门面。
 * 使用字段级 {@code @DubboReference}，使 postmaster 在独立部署时也能通过 RPC 调用
 * social 模块；all-in-one 部署时 Dubbo 自动短路为本地调用。
 */
@Component
public class ConversationSyncFacade {

    @DubboReference(check = false)
    private ConversationSyncService conversationSyncService;

    /** Spring 使用的默认构造器（@DubboReference 字段注入）。 */
    public ConversationSyncFacade() {}

    /** 单元测试用构造器。 */
    ConversationSyncFacade(ConversationSyncService conversationSyncService) {
        this.conversationSyncService = conversationSyncService;
    }

    public void createIfNew(ConversationSyncCommand cmd) {
        conversationSyncService.createIfNew(cmd);
    }

    public void sync(ConversationSyncCommand cmd) {
        conversationSyncService.sync(cmd);
    }

    public void markRead(String ownerUserId, String conversationId, long readSeq) {
        conversationSyncService.markRead(ownerUserId, conversationId, readSeq);
    }
}
