package com.cheeseocean.im.postmaster.history;

import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.history.MessageHistoryRepository;
import org.springframework.stereotype.Service;

/**
 * 历史块持久化：一个 HistoryEvent 内的消息按 blockNo 分桶后，
 * 用 unordered bulk（id mapping + message block + attachment metadata）落 Mongo，
 * 避免逐条 save/upsert 在高吞吐下打爆单节点写入（ASSESSMENT P1-8）。
 * upsert 以确定性 _id 幂等，队列重放不会产生重复数据。
 */
@Service
public class BlockHistoryPersistenceService {

    private final MessageHistoryRepository messageHistoryRepository;

    public BlockHistoryPersistenceService(MessageHistoryRepository messageHistoryRepository) {
        this.messageHistoryRepository = messageHistoryRepository;
    }

    public void persist(HistoryEvent event) {
        messageHistoryRepository.persist(event);
    }
}
