package com.cheeseocean.im.postmaster.history;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.util.BlockIndexUtil;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 历史块持久化：一个 HistoryEvent 内的消息按 blockNo 分桶后，
 * 用两次 unordered bulk（id mapping + message block）落 Mongo，
 * 避免逐条 save/upsert 在高吞吐下打爆单节点写入（ASSESSMENT P1-8）。
 * upsert 以确定性 _id 幂等，队列重放不会产生重复数据。
 */
@Service
public class BlockHistoryPersistenceService {

    private final MongoTemplate mongoTemplate;

    public BlockHistoryPersistenceService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void persist(HistoryEvent event) {
        List<Message> messages = event.getMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }

        // mapping 先于 block 写入，保持与旧逐条写一致的可见性顺序
        persistMappings(event.getConversationId(), messages);

        Map<Long, List<Message>> byBlock = messages.stream()
                .collect(Collectors.groupingBy(message -> BlockIndexUtil.blockNo(message.getSeq())));

        BulkOperations blockOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MessageBlockDoc.class);
        for (Map.Entry<Long, List<Message>> entry : byBlock.entrySet()) {
            blockOps.upsert(blockQuery(event.getConversationId(), entry.getKey()),
                    blockUpdate(event.getConversationId(), entry.getKey(), entry.getValue()));
        }
        blockOps.execute();
    }

    private void persistMappings(String conversationId, List<Message> messages) {
        BulkOperations mappingOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, MessageIdMappingDoc.class);
        for (Message message : messages) {
            String docId = conversationId + ":" + message.getClientMsgId();
            Update update = new Update()
                    .setOnInsert("createdAt", Instant.now())
                    .set("conversationId", conversationId)
                    .set("clientMsgId", message.getClientMsgId())
                    .set("serverMsgId", message.getServerMsgId())
                    .set("seq", message.getSeq())
                    .set("senderId", message.getSenderId())
                    .set("sendTime", message.getSendTime());
            mappingOps.upsert(Query.query(Criteria.where("_id").is(docId)), update);
        }
        mappingOps.execute();
    }

    private Query blockQuery(String conversationId, long blockNo) {
        return Query.query(Criteria.where("_id").is(conversationId + ":" + blockNo));
    }

    private Update blockUpdate(String conversationId, long blockNo, List<Message> messages) {
        long startSeq = blockNo * BlockIndexUtil.BLOCK_SIZE + 1L;
        Update update = new Update()
                .setOnInsert("_id", conversationId + ":" + blockNo)
                .setOnInsert("conversationId", conversationId)
                .setOnInsert("blockNo", blockNo)
                .setOnInsert("createdAt", Instant.now())
                .setOnInsert("startSeq", startSeq)
                .setOnInsert("endSeq", startSeq + BlockIndexUtil.BLOCK_SIZE - 1L)
                .set("updatedAt", Instant.now());

        for (Message message : messages) {
            update.set("messages." + BlockIndexUtil.index(message.getSeq()), toSlot(message));
        }
        return update;
    }

    private MessageSlot toSlot(Message message) {
        MessageSlot slot = new MessageSlot();
        slot.setSeq(message.getSeq());
        slot.setClientMsgId(message.getClientMsgId());
        slot.setServerMsgId(message.getServerMsgId());
        slot.setSenderId(message.getSenderId());
        slot.setReceiverId(message.getReceiverId());
        slot.setGroupId(message.getGroupId());
        slot.setSessionType(message.getChatType() == null ? null : message.getChatType().getCode());
        slot.setContentType(message.getContentType() == null ? null : message.getContentType().getCode());
        slot.setContent(message.getContent());
        slot.setSendTime(message.getSendTime());
        slot.setCreateTime(message.getCreateTime());
        slot.setStatus(message.getStatus() == null ? null : message.getStatus().getCode());
        slot.setPlatformType(message.getPlatformType() == null ? null : message.getPlatformType().getCode());
        slot.setUniqueId(message.getUniqueId());
        slot.setSource(message.getSource() == null ? null : message.getSource().getCode());
        slot.setOptions(message.getOptions());
        slot.setAttributes(message.getAttributes());
        return slot;
    }
}
