package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.api.GatewayPushService;
import com.cheeseocean.im.common.api.MessageDeliveryService;
import com.cheeseocean.im.common.api.MessagePushService;
import com.cheeseocean.im.common.api.MessageStoreService;
import com.cheeseocean.im.common.dto.DeliveryAck;
import com.cheeseocean.im.common.dto.DeliveryCommand;
import com.cheeseocean.im.common.dto.DeliveryResult;
import com.cheeseocean.im.common.dto.GatewayPushResult;
import com.cheeseocean.im.common.dto.IngressEvent;
import com.cheeseocean.im.common.dto.MessageProto;
import com.cheeseocean.im.common.entity.DeliveryState;
import com.cheeseocean.im.common.entity.DeliveryTask;
import com.cheeseocean.im.common.entity.StoredMessage;
import com.cheeseocean.im.common.utils.IdGenerator;
import com.cheeseocean.im.postman.auth.MessageAuthFacade;
import com.cheeseocean.im.postman.config.MessageFlowProperties;
import com.cheeseocean.im.postman.metrics.MessageFlowMetrics;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@DubboService(interfaceClass = MessageDeliveryService.class)
public class MessageDeliveryServiceImpl implements MessageDeliveryService {

    private final MessageIdempotencyService idempotencyService;
    private final DeliveryStateMachine stateMachine;
    private final MessageStoreService messageStoreService;
    private final GatewayPushService gatewayPushService;
    private final MessagePushService messagePushService;
    private final DeliveryCompensationService deliveryCompensationService;
    private final GroupFanoutPlanner groupFanoutPlanner;
    private final MessageAuthFacade messageAuthFacade;
    private final GroupMembershipFacade groupMembershipFacade;
    private final ConversationSeqService conversationSeqService;
    private final IngressEventPublisher ingressEventPublisher;
    private final MessageFlowProperties messageFlowProperties;
    private final MessageFlowMetrics messageFlowMetrics;

    @Autowired
    public MessageDeliveryServiceImpl(MessageIdempotencyService idempotencyService,
                                      DeliveryStateMachine stateMachine,
                                      MessageStoreService messageStoreService,
                                      GatewayPushService gatewayPushService,
                                      MessagePushService messagePushService,
                                      DeliveryCompensationService deliveryCompensationService,
                                      GroupFanoutPlanner groupFanoutPlanner,
                                      MessageAuthFacade messageAuthFacade,
                                      GroupMembershipFacade groupMembershipFacade,
                                      ConversationSeqService conversationSeqService,
                                      IngressEventPublisher ingressEventPublisher,
                                      MessageFlowProperties messageFlowProperties,
                                      MessageFlowMetrics messageFlowMetrics) {
        this.idempotencyService = idempotencyService;
        this.stateMachine = stateMachine;
        this.messageStoreService = messageStoreService;
        this.gatewayPushService = gatewayPushService;
        this.messagePushService = messagePushService;
        this.deliveryCompensationService = deliveryCompensationService;
        this.groupFanoutPlanner = groupFanoutPlanner;
        this.messageAuthFacade = messageAuthFacade;
        this.groupMembershipFacade = groupMembershipFacade;
        this.conversationSeqService = conversationSeqService;
        this.ingressEventPublisher = ingressEventPublisher;
        this.messageFlowProperties = messageFlowProperties;
        this.messageFlowMetrics = messageFlowMetrics;
    }

    public MessageDeliveryServiceImpl(MessageIdempotencyService idempotencyService,
                                      DeliveryStateMachine stateMachine,
                                      MessageStoreService messageStoreService,
                                      GatewayPushService gatewayPushService,
                                      MessagePushService messagePushService,
                                      DeliveryCompensationService deliveryCompensationService,
                                      GroupFanoutPlanner groupFanoutPlanner,
                                      MessageAuthFacade messageAuthFacade,
                                      GroupMembershipFacade groupMembershipFacade,
                                      ConversationSeqService conversationSeqService,
                                      IngressEventPublisher ingressEventPublisher,
                                      MessageFlowProperties messageFlowProperties) {
        this(idempotencyService, stateMachine, messageStoreService, gatewayPushService, messagePushService,
                deliveryCompensationService, groupFanoutPlanner, messageAuthFacade, groupMembershipFacade,
                conversationSeqService, ingressEventPublisher, messageFlowProperties, null);
    }

    public MessageDeliveryServiceImpl(MessageIdempotencyService idempotencyService,
                                      DeliveryStateMachine stateMachine,
                                      MessageStoreService messageStoreService,
                                      GatewayPushService gatewayPushService,
                                      MessagePushService messagePushService,
                                      DeliveryCompensationService deliveryCompensationService,
                                      GroupFanoutPlanner groupFanoutPlanner,
                                      MessageAuthFacade messageAuthFacade) {
        this(idempotencyService, stateMachine, messageStoreService, gatewayPushService, messagePushService,
                deliveryCompensationService, groupFanoutPlanner, messageAuthFacade,
                null, null, null, new MessageFlowProperties(), null);
    }

    public MessageDeliveryServiceImpl(MessageIdempotencyService idempotencyService,
                                      DeliveryStateMachine stateMachine,
                                      MessageStoreService messageStoreService,
                                      GatewayPushService gatewayPushService,
                                      MessagePushService messagePushService,
                                      DeliveryCompensationService deliveryCompensationService,
                                      GroupFanoutPlanner groupFanoutPlanner) {
        this(idempotencyService, stateMachine, messageStoreService, gatewayPushService, messagePushService,
                deliveryCompensationService, groupFanoutPlanner, null);
    }

    public MessageDeliveryServiceImpl(MessageIdempotencyService idempotencyService,
                                      DeliveryStateMachine stateMachine,
                                      MessageStoreService messageStoreService,
                                      GatewayPushService gatewayPushService,
                                      MessagePushService messagePushService,
                                      DeliveryCompensationService deliveryCompensationService) {
        this(idempotencyService, stateMachine, messageStoreService, gatewayPushService, messagePushService,
                deliveryCompensationService, new GroupFanoutPlanner(500), null);
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
        if (messageFlowProperties != null && messageFlowProperties.isAsyncIngressEnabled()) {
            return acceptAndPublish(command);
        }
        if (messageFlowMetrics != null) {
            messageFlowMetrics.recordLegacyFallback();
        }
        return legacyDeliverFresh(command);
    }

    private DeliveryResult acceptAndPublish(DeliveryCommand command) {
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

    private DeliveryResult legacyDeliverFresh(DeliveryCommand command) {
        StoredMessage persisted = messageStoreService.saveMessage(toStoredMessage(command));
        if (command.isGroupDelivery()) {
            return deliverGroup(command, persisted, resolveGroupTargets(command));
        }
        DeliveryTask task = stateMachine.persisted(command.getDeviceId(), persisted);
        GatewayPushResult pushResult = gatewayPushService.pushToUser(command.getReceiverId(), toMessageProto(command, persisted));
        task = stateMachine.afterGatewayAttempt(task, pushResult);

        DeliveryResult result = new DeliveryResult();
        result.setSuccess(true);
        result.setServerMsgId(persisted.getServerMsgId());

        if (task.getState() == DeliveryState.ONLINE_CONFIRMED) {
            result.setStatus(task.getState().name());
            result.setState(task.getState());
            result.setReceiverOnline(true);
            idempotencyService.remember(command.getSenderId(), command.getConversationId(), command.getClientMsgId(), result);
            return result;
        }

        long inboxSeq = messageStoreService.saveOfflineMessage(toMessageProto(command, persisted));
        messagePushService.pushOffline(command.getReceiverId(), toMessageProto(command, persisted));
        task = stateMachine.pushTriggered(task);
        deliveryCompensationService.schedule(task);

        result.setStatus(task.getState().name());
        result.setState(task.getState());
        result.setReceiverOnline(false);
        result.setStoredMessageId(inboxSeq);
        idempotencyService.remember(command.getSenderId(), command.getConversationId(), command.getClientMsgId(), result);
        return result;
    }

    private DeliveryResult deliverGroup(DeliveryCommand command, StoredMessage persisted, List<String> targetUserIds) {
        GroupFanoutPlanner.FanoutPlan plan = groupFanoutPlanner.plan(command, targetUserIds);
        Long firstInboxSeq = null;
        for (GroupFanoutPlanner.FanoutBatch batch : plan.getBatches()) {
            List<Long> savedSequences = messageStoreService.saveOfflineMessages(
                    toMessageProto(command, persisted), batch.getReceiverIds());
            if (firstInboxSeq == null && !savedSequences.isEmpty()) {
                firstInboxSeq = savedSequences.get(0);
            }
        }

        DeliveryResult result = new DeliveryResult();
        result.setSuccess(true);
        result.setServerMsgId(persisted.getServerMsgId());
        result.setStatus(DeliveryState.PUSH_TRIGGERED.name());
        result.setState(DeliveryState.PUSH_TRIGGERED);
        result.setReceiverOnline(false);
        result.setStoredMessageId(firstInboxSeq);
        idempotencyService.remember(command.getSenderId(), command.getConversationId(), command.getClientMsgId(), result);
        return result;
    }

    private List<String> resolveGroupTargets(DeliveryCommand command) {
        if (!command.getTargetUserIds().isEmpty()) {
            return command.getTargetUserIds();
        }
        if (groupMembershipFacade == null) {
            throw new IllegalStateException("GroupMembershipFacade is not configured for group delivery");
        }
        List<String> members = groupMembershipFacade.loadTargets(command.getConversationId());
        if (members == null || members.isEmpty()) {
            throw new IllegalStateException("No group members resolved for conversation " + command.getConversationId());
        }
        return members;
    }

    private StoredMessage toStoredMessage(DeliveryCommand command) {
        StoredMessage message = new StoredMessage();
        message.setServerMsgId(command.getClientMsgId().replace("c-", "s-"));
        message.setClientMsgId(command.getClientMsgId());
        message.setConversationId(command.getConversationId());
        message.setSenderId(command.getSenderId());
        message.setReceiverId(command.getReceiverId());
        message.setContent(command.getContent());
        message.setContentType(command.getContentType());
        message.setAttachedInfo(command.getAttachedInfo());
        return message;
    }

    private MessageProto toMessageProto(DeliveryCommand command, StoredMessage stored) {
        MessageProto proto = new MessageProto();
        proto.setClientMsgId(command.getClientMsgId());
        proto.setServerMsgId(stored.getServerMsgId());
        proto.setConversationId(command.getConversationId());
        proto.setSenderId(command.getSenderId());
        proto.setReceiverId(command.getReceiverId());
        proto.setContent(command.getContent());
        proto.setContentType(command.getContentType());
        proto.setSessionType(command.getSessionType());
        proto.setAttachedInfo(command.getAttachedInfo());
        return proto;
    }
}
