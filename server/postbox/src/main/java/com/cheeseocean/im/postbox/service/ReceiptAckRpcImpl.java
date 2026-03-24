package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.receipt.ReceiptAckReq;
import com.cheeseocean.im.common.api.rpc.ReceiptAckRpc;
import com.cheeseocean.im.common.core.enums.ReceiptType;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.postbox.history.MessageIdMappingDoc;
import com.cheeseocean.im.postbox.history.MessageIdMappingRepository;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.Optional;

@DubboService(interfaceClass = ReceiptAckRpc.class)
public class ReceiptAckRpcImpl implements ReceiptAckRpc {

    private final ConversationReceiptService conversationReceiptService;
    private final ConversationStateStore conversationStateStore;
    private final MessageIdMappingRepository mappingRepository;

    public ReceiptAckRpcImpl(ConversationReceiptService conversationReceiptService,
                             ConversationStateStore conversationStateStore,
                             MessageIdMappingRepository mappingRepository) {
        this.conversationReceiptService = conversationReceiptService;
        this.conversationStateStore = conversationStateStore;
        this.mappingRepository = mappingRepository;
    }

    @Override
    public void apply(ReceiptAckReq req) {
        if (req == null || req.getUserId() == null || req.getAckType() == null) {
            return;
        }
        if (req.getAckType() == ReceiptType.READ_CURSOR) {
            applyRead(req);
            return;
        }
        if (req.getAckType() == ReceiptType.RECEIVED || req.getAckType() == ReceiptType.DELIVERED) {
            applyReceived(req);
        }
    }

    private void applyRead(ReceiptAckReq req) {
        conversationReceiptService.applyReadCursor(req.getUserId(), req.getConversationId(), req.getSeq());
    }

    private void applyReceived(ReceiptAckReq req) {
        ResolvedReceipt resolved = resolve(req);
        if (resolved == null) {
            return;
        }
        conversationStateStore.setUserMaxSeq(req.getUserId(), resolved.conversationId(), resolved.seq());
    }

    private ResolvedReceipt resolve(ReceiptAckReq req) {
        if (req.getConversationId() != null && req.getSeq() != null) {
            return new ResolvedReceipt(req.getConversationId(), req.getSeq());
        }
        if (req.getServerMsgId() == null) {
            return null;
        }
        Optional<MessageIdMappingDoc> mapping = mappingRepository.findByServerMsgId(req.getServerMsgId());
        if (mapping.isEmpty() || mapping.get().getConversationId() == null || mapping.get().getSeq() == null) {
            return null;
        }
        return new ResolvedReceipt(mapping.get().getConversationId(), mapping.get().getSeq());
    }

    private record ResolvedReceipt(String conversationId, Long seq) {
    }
}
