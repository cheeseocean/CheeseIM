package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.MessageDeliveryService;
import com.cheeseocean.im.common.api.MessagePushService;
import com.cheeseocean.im.common.api.MessageStoreService;
import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.dto.IngressEvent;
import com.cheeseocean.im.common.utils.IdGenerator;
import com.cheeseocean.im.postman.auth.MessageAuthFacade;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@DubboService(interfaceClass = MessageDeliveryService.class)
public class MessageDeliveryServiceImpl implements MessageDeliveryService {

    private final MessageIdempotencyService idempotencyService;
    private final MessageStoreService messageStoreService;
    private final MessagePushService messagePushService;
    private final MessageAuthFacade messageAuthFacade;
    private final ConversationSeqService conversationSeqService;
    private final IngressEventPublisher ingressEventPublisher;

    @Autowired
    public MessageDeliveryServiceImpl(MessageIdempotencyService idempotencyService,
                                      MessageStoreService messageStoreService,
                                      MessagePushService messagePushService,
                                      MessageAuthFacade messageAuthFacade,
                                      ConversationSeqService conversationSeqService,
                                      IngressEventPublisher ingressEventPublisher) {
        this.idempotencyService = idempotencyService;
        this.messageStoreService = messageStoreService;
        this.messagePushService = messagePushService;
        this.messageAuthFacade = messageAuthFacade;
        this.conversationSeqService = conversationSeqService;
        this.ingressEventPublisher = ingressEventPublisher;
    }

    @Override
    public DeliveryResult deliver(DeliveryCommand command) {
        if (messageAuthFacade != null) {
            messageAuthFacade.authorizeSend(command);
        }
        return idempotencyService.findExisting(command.getSenderId(), command.getConversationId(), command.getClientMsgId())
                .orElseGet(() -> deliverFresh(command));
    }

    @Override
    public DeliveryResult ack(DeliveryAck ack) {
        DeliveryResult result = messageStoreService.applyAck(ack);
        if ("READ".equals(ack.getAckType()) || "RECALL".equals(ack.getAckType())) {
            messagePushService.cancelPending(ack.getServerMsgId(), ack.getUserId());
        }
        return result;
    }

    private DeliveryResult deliverFresh(DeliveryCommand command) {
        if (conversationSeqService == null || ingressEventPublisher == null) {
            throw new IllegalStateException("Async ingress dependencies are not configured");
        }
        String messageId = IdGenerator.generateMsgId();
        long conversationSeq = conversationSeqService.nextSeq(command.getConversationId());
        IngressEvent event = IngressEvent.from(command, messageId, conversationSeq, IdGenerator.generateOperationId());
        ingressEventPublisher.publish(event);
        DeliveryResult accepted = DeliveryResult.accepted(messageId, conversationSeq);
        idempotencyService.remember(command.getSenderId(), command.getConversationId(), command.getClientMsgId(), accepted);
        return accepted;
    }
}
