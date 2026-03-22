package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.common.api.dto.receipt.ReceiptAckReq;
import com.cheeseocean.im.common.api.rpc.ReceiptAckRpc;
import com.cheeseocean.im.common.core.constants.RedisKeys;
import com.cheeseocean.im.postbox.history.MessageIdMappingDoc;
import com.cheeseocean.im.postbox.history.MessageIdMappingRepository;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

@DubboService(interfaceClass = ReceiptAckRpc.class)
public class ReceiptAckRpcImpl implements ReceiptAckRpc {

    private final StringRedisTemplate redisTemplate;
    private final MessageIdMappingRepository mappingRepository;

    public ReceiptAckRpcImpl(StringRedisTemplate redisTemplate,
                             MessageIdMappingRepository mappingRepository) {
        this.redisTemplate = redisTemplate;
        this.mappingRepository = mappingRepository;
    }

    @Override
    public void apply(ReceiptAckReq req) {
        if (req == null || req.getUserId() == null || req.getAckType() == null) {
            return;
        }
        if ("READ".equals(req.getAckType())) {
            applyRead(req);
            return;
        }
        if ("RECEIVED".equals(req.getAckType())) {
            applyReceived(req);
        }
    }

    private void applyRead(ReceiptAckReq req) {
        if (req.getConversationId() == null || req.getSeq() == null) {
            return;
        }
        redisTemplate.opsForValue().set(
                RedisKeys.userReadSeq(req.getUserId(), req.getConversationId()),
                String.valueOf(req.getSeq()));
    }

    private void applyReceived(ReceiptAckReq req) {
        ResolvedReceipt resolved = resolve(req);
        if (resolved == null) {
            return;
        }
        redisTemplate.opsForValue().set(
                RedisKeys.userMaxSeq(req.getUserId(), resolved.conversationId()),
                String.valueOf(resolved.seq()));
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
