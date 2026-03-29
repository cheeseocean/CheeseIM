package com.cheeseocean.im.social.service.conversation;

import com.cheeseocean.im.common.api.conversation.ConversationSyncCommand;
import com.cheeseocean.im.common.api.conversation.ConversationSyncService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 用户会话同步服务实现。
 *
 * <p>生命周期：
 * <ul>
 *   <li>懒创建 — {@code cmd.newConversation()} 为 true 时为所有参与者插入业务会话记录及偏移量记录，幂等。</li>
 *   <li>实时更新 — 每批消息落库后仅推进各参与者的 maxSeq（写入偏移量表）。</li>
 *   <li>已读同步 — markRead 将 readSeq 交由 {@link ReadSeqPersistenceWriter} 异步持久化。</li>
 * </ul>
 */
@Service
@DubboService
public class ConversationSyncServiceImpl implements ConversationSyncService {

    private final ConversationLifecycleService conversationLifecycleService;
    private final ReadSeqPersistenceWriter readSeqPersistenceWriter;

    public ConversationSyncServiceImpl(ConversationLifecycleService conversationLifecycleService,
                                       ReadSeqPersistenceWriter readSeqPersistenceWriter) {
        this.conversationLifecycleService = conversationLifecycleService;
        this.readSeqPersistenceWriter = readSeqPersistenceWriter;
    }

    @Override
    public void createIfNew(ConversationSyncCommand cmd) {
        conversationLifecycleService.createIfNew(cmd);
    }

    @Override
    public void sync(ConversationSyncCommand cmd) {
        conversationLifecycleService.sync(cmd);
    }

    @Override
    public void markRead(String ownerUserId, String conversationId, long readSeq) {
        readSeqPersistenceWriter.enqueue(ownerUserId, conversationId, readSeq);
    }
}
