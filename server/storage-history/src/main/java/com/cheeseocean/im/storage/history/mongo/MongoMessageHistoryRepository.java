package com.cheeseocean.im.storage.history.mongo;

import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.event.HistoryEvent;
import com.cheeseocean.im.common.core.history.MessageHistoryRepository;
import com.cheeseocean.im.common.core.history.model.AttachmentMetadata;
import com.cheeseocean.im.common.core.history.model.MessageBlock;
import com.cheeseocean.im.common.core.history.model.MessageIdMapping;
import com.cheeseocean.im.common.core.history.model.MessageMutation;
import com.cheeseocean.im.common.core.history.model.MessageSlot;
import com.cheeseocean.im.common.core.util.BlockIndexUtil;
import com.cheeseocean.im.storage.history.mongo.document.AttachmentMetadataDoc;
import com.cheeseocean.im.storage.history.mongo.document.MessageBlockDoc;
import com.cheeseocean.im.storage.history.mongo.document.MessageIdMappingDoc;
import com.cheeseocean.im.storage.history.mongo.document.MessageMutationDoc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.types.Binary;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Mongo 历史存储 adapter，集中维护 collection、索引、批量写和模型转换语义。 */
public class MongoMessageHistoryRepository implements MessageHistoryRepository {
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public MongoMessageHistoryRepository(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void persist(HistoryEvent event) {
        List<Message> messages = event.getMessages();
        if (messages == null || messages.isEmpty()) return;
        BulkOperations mappings = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED,
                MessageIdMappingDoc.class);
        BulkOperations blocks = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED,
                MessageBlockDoc.class);
        BulkOperations attachments = null;
        for (Message message : messages) {
            Instant now = Instant.now();
            String mappingId = event.getConversationId() + ":" + message.getClientMsgId();
            mappings.upsert(Query.query(Criteria.where("_id").is(mappingId)
                            .and("serverMsgId").is(message.getServerMsgId())),
                    new Update()
                            .setOnInsert("createdAt", now)
                            .set("conversationId", event.getConversationId())
                            .set("clientMsgId", message.getClientMsgId())
                            .set("serverMsgId", message.getServerMsgId())
                            .set("seq", message.getSeq())
                            .set("senderId", message.getSenderId())
                            .set("sendTime", message.getSendTime()));
            if (message.getContentType() != null && message.getContentType().hasAttachment()) {
                String attachmentId = attachmentId(message.getContent());
                if (attachmentId != null) {
                    if (attachments == null) {
                        attachments = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED,
                                AttachmentMetadataDoc.class);
                    }
                    attachments.upsert(Query.query(Criteria.where("_id").is(attachmentId)),
                            new Update()
                                    .setOnInsert("createdAt", now)
                                    .set("conversationId", event.getConversationId())
                                    .set("serverMsgId", message.getServerMsgId())
                                    .set("clientMsgId", message.getClientMsgId())
                                    .set("seq", message.getSeq())
                                    .set("senderId", message.getSenderId())
                                    .set("contentType", message.getContentType().getCode())
                                    .set("sendTime", message.getSendTime()));
                }
            }
        }
        mappings.execute();
        if (attachments != null) attachments.execute();

        Map<Long, List<Message>> byBlock = messages.stream()
                .collect(Collectors.groupingBy(message -> BlockIndexUtil.blockNo(message.getSeq())));
        for (Map.Entry<Long, List<Message>> entry : byBlock.entrySet()) {
            long blockNo = entry.getKey();
            long start = blockNo * BlockIndexUtil.BLOCK_SIZE + 1L;
            Update update = new Update()
                    .setOnInsert("_id", event.getConversationId() + ":" + blockNo)
                    .setOnInsert("conversationId", event.getConversationId())
                    .setOnInsert("blockNo", blockNo)
                    .setOnInsert("createdAt", Instant.now())
                    .setOnInsert("startSeq", start)
                    .setOnInsert("endSeq", start + BlockIndexUtil.BLOCK_SIZE - 1)
                    .set("updatedAt", Instant.now());
            entry.getValue().forEach(message -> update.set(
                    "messages." + BlockIndexUtil.index(message.getSeq()), slot(message)));
            blocks.upsert(Query.query(Criteria.where("_id")
                    .is(event.getConversationId() + ":" + blockNo)
                    .and("conversationId").is(event.getConversationId())), update);
        }
        blocks.execute();
    }

    @Override
    public List<MessageBlock> findRecentBlocks(String conversationId, int limit, int maxWindows) {
        MessageBlockDoc latest = mongoTemplate.findOne(Query.query(Criteria.where("conversationId")
                        .is(conversationId))
                .with(Sort.by(Sort.Direction.DESC, "blockNo")).limit(1), MessageBlockDoc.class);
        if (latest == null || latest.getBlockNo() == null) return List.of();
        List<MessageBlock> result = new ArrayList<>();
        long cursor = latest.getBlockNo();
        int windows = 0;
        int size = Math.max(1, (int) Math.ceil((double) limit / BlockIndexUtil.BLOCK_SIZE));
        while (cursor >= 0
                && result.stream().mapToInt(block -> block.getMessages().size()).sum() < limit
                && windows++ < maxWindows) {
            long begin = Math.max(0, cursor - size + 1);
            result.addAll(mongoTemplate.find(Query.query(Criteria.where("conversationId")
                                    .is(conversationId).and("blockNo").gte(begin).lte(cursor))
                            .with(Sort.by(Sort.Direction.DESC, "blockNo")), MessageBlockDoc.class)
                    .stream().map(this::toModel).toList());
            cursor = begin - 1;
        }
        return result;
    }

    @Override
    public List<MessageBlock> findBlocksBySeqRange(String conversationId, long beginSeq, long endSeq) {
        return mongoTemplate.find(Query.query(Criteria.where("conversationId").is(conversationId)
                        .and("blockNo").gte(BlockIndexUtil.blockNo(beginSeq)).lte(BlockIndexUtil.blockNo(endSeq)))
                .with(Sort.by(Sort.Direction.ASC, "blockNo")), MessageBlockDoc.class)
                .stream().map(this::toModel).toList();
    }

    @Override
    public List<MessageIdMapping> findRecentMappings(int limit) {
        return mongoTemplate.find(new Query().with(Sort.by(Sort.Direction.DESC, "sendTime"))
                        .limit(Math.max(1, limit)), MessageIdMappingDoc.class)
                .stream().map(this::toModel).toList();
    }

    @Override
    public MessageSlot findSlot(String conversationId, long seq) {
        MessageBlockDoc block = mongoTemplate.findOne(Query.query(Criteria.where("_id")
                        .is(BlockIndexUtil.docId(conversationId, seq))
                        .and("conversationId").is(conversationId)), MessageBlockDoc.class);
        if (block == null) return null;
        return block.getMessages().stream()
                .filter(slot -> slot != null && Long.valueOf(seq).equals(slot.getSeq()))
                .map(this::normalizeSlot)
                .findFirst()
                .orElse(null);
    }

    @Override
    public AttachmentMetadata findAttachmentMetadata(String attachmentId) {
        return toModel(mongoTemplate.findById(attachmentId, AttachmentMetadataDoc.class));
    }

    @Override
    public List<MessageMutation> findRevokedMutations(List<String> serverMsgIds) {
        Set<String> ids = new HashSet<>();
        for (String id : serverMsgIds) {
            if (id != null && !id.isBlank()) ids.add(id + ":REVOKED");
        }
        if (ids.isEmpty()) return List.of();
        return mongoTemplate.find(Query.query(Criteria.where("_id").in(ids)), MessageMutationDoc.class)
                .stream().map(this::toModel).toList();
    }

    @Override
    public MessageIdMapping findMappingByServerMessageId(String serverMsgId) {
        return toModel(mongoTemplate.findOne(Query.query(Criteria.where("serverMsgId").is(serverMsgId)),
                MessageIdMappingDoc.class));
    }

    @Override
    public MessageMutation findMutationById(String mutationId) {
        return toModel(mongoTemplate.findById(mutationId, MessageMutationDoc.class));
    }

    @Override
    public MessageMutation upsertMutation(MessageMutation mutation) {
        try {
            return toModel(mongoTemplate.findAndModify(
                    Query.query(Criteria.where("_id").is(mutation.getId())),
                    new Update()
                            .setOnInsert("serverMsgId", mutation.getServerMsgId())
                            .setOnInsert("conversationId", mutation.getConversationId())
                            .setOnInsert("mutationType", mutation.getMutationType())
                            .setOnInsert("operatorUserId", mutation.getOperatorUserId())
                            .setOnInsert("operatorName", mutation.getOperatorName())
                            .setOnInsert("targetSenderId", mutation.getTargetSenderId())
                            .setOnInsert("targetSenderName", mutation.getTargetSenderName())
                            .setOnInsert("reason", mutation.getReason())
                            .setOnInsert("mutationVersion", mutation.getMutationVersion())
                            .setOnInsert("createdAt", mutation.getCreatedAt()),
                    FindAndModifyOptions.options().upsert(true).returnNew(true), MessageMutationDoc.class));
        } catch (DuplicateKeyException ignored) {
            // 并发撤回可能同时尝试插入同一确定性 _id；adapter 在持久化边界收敛为幂等读取。
            return findMutationById(mutation.getId());
        }
    }

    @Override
    public List<MessageMutation> findMutationsAfter(String conversationId, Instant afterCreatedAt,
                                                     String afterMutationId, int limit) {
        Criteria afterCursor = new Criteria().orOperator(
                Criteria.where("createdAt").gt(afterCreatedAt),
                new Criteria().andOperator(Criteria.where("createdAt").is(afterCreatedAt),
                        Criteria.where("_id").gt(afterMutationId == null ? "" : afterMutationId)));
        return mongoTemplate.find(Query.query(Criteria.where("conversationId").is(conversationId)
                                .andOperator(afterCursor))
                        .with(Sort.by(Sort.Direction.ASC, "createdAt")
                                .and(Sort.by(Sort.Direction.ASC, "_id")))
                        .limit(limit), MessageMutationDoc.class)
                .stream().map(this::toModel).toList();
    }

    private MessageBlock toModel(MessageBlockDoc document) {
        if (document == null) return null;
        MessageBlock model = new MessageBlock();
        model.setId(document.getId());
        model.setConversationId(document.getConversationId());
        model.setBlockNo(document.getBlockNo());
        model.setStartSeq(document.getStartSeq());
        model.setEndSeq(document.getEndSeq());
        model.setMessages(document.getMessages().stream().map(this::normalizeSlot).toList());
        model.setCreatedAt(document.getCreatedAt());
        model.setUpdatedAt(document.getUpdatedAt());
        return model;
    }

    private MessageIdMapping toModel(MessageIdMappingDoc document) {
        if (document == null) return null;
        MessageIdMapping model = new MessageIdMapping();
        model.setId(document.getId());
        model.setConversationId(document.getConversationId());
        model.setClientMsgId(document.getClientMsgId());
        model.setServerMsgId(document.getServerMsgId());
        model.setSeq(document.getSeq());
        model.setSenderId(document.getSenderId());
        model.setSendTime(document.getSendTime());
        model.setCreatedAt(document.getCreatedAt());
        return model;
    }

    private MessageMutation toModel(MessageMutationDoc document) {
        if (document == null) return null;
        MessageMutation model = new MessageMutation();
        model.setId(document.getId());
        model.setServerMsgId(document.getServerMsgId());
        model.setConversationId(document.getConversationId());
        model.setMutationType(document.getMutationType());
        model.setOperatorUserId(document.getOperatorUserId());
        model.setOperatorName(document.getOperatorName());
        model.setTargetSenderId(document.getTargetSenderId());
        model.setTargetSenderName(document.getTargetSenderName());
        model.setReason(document.getReason());
        model.setMutationVersion(document.getMutationVersion());
        model.setCreatedAt(document.getCreatedAt());
        return model;
    }

    private AttachmentMetadata toModel(AttachmentMetadataDoc document) {
        if (document == null) return null;
        AttachmentMetadata model = new AttachmentMetadata();
        model.setId(document.getId());
        model.setConversationId(document.getConversationId());
        model.setServerMsgId(document.getServerMsgId());
        model.setClientMsgId(document.getClientMsgId());
        model.setSeq(document.getSeq());
        model.setSenderId(document.getSenderId());
        model.setContentType(document.getContentType());
        model.setSendTime(document.getSendTime());
        model.setCreatedAt(document.getCreatedAt());
        return model;
    }

    /** Mongo 在 Object 字段上可能返回 BSON Binary；端口边界统一规范为 byte[]。 */
    private MessageSlot normalizeSlot(MessageSlot slot) {
        if (slot != null && slot.getContent() instanceof Binary binary) {
            slot.setContent(binary.getData());
        }
        return slot;
    }

    private String attachmentId(byte[] content) {
        try {
            String id = content == null ? null : objectMapper.readTree(content).path("attachmentId").asText(null);
            return id == null || id.isBlank() ? null : id;
        } catch (Exception ignored) {
            return null;
        }
    }

    private MessageSlot slot(Message message) {
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
