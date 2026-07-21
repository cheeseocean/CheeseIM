package com.cheeseocean.im.postmaster.listener;

import com.cheeseocean.im.common.api.conversation.ConversationService;
import com.cheeseocean.im.common.api.dto.message.Message;
import com.cheeseocean.im.common.api.event.GroupFanoutEvent;
import com.cheeseocean.im.common.api.group.GroupMemberPage;
import com.cheeseocean.im.common.core.constants.TopicNames;
import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.cheeseocean.im.common.core.store.fanout.GroupFanoutJobStore;
import com.cheeseocean.im.postmaster.sender.MessageProducer;
import com.cheeseocean.im.postmaster.service.GroupFanoutPlanner;
import com.cheeseocean.im.postmaster.service.GroupMembershipFacade;
import com.cheeseocean.im.postmaster.service.UserMaxSeqPersistenceWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 普通群写扩散 worker，隔离成员枚举与 O(成员数) 投递对 ingress consumer 的阻塞。
 */
@Component
public class GroupFanoutEventListener {

    private final ObjectMapper objectMapper;
    private final GroupMembershipFacade groupMembershipFacade;
    private final GroupFanoutPlanner planner;
    private final MessageProducer messageProducer;
    private final ConversationStateStore conversationStateStore;
    private final UserMaxSeqPersistenceWriter persistenceWriter;
    private final GroupFanoutJobStore fanoutJobStore;
    private final int memberPageSize;
    private final long jobLeaseMillis;

    @DubboReference(check = false, retries = 0)
    private ConversationService conversationService;

    public GroupFanoutEventListener(ObjectMapper objectMapper,
                                    GroupMembershipFacade groupMembershipFacade,
                                    GroupFanoutPlanner planner,
                                    MessageProducer messageProducer,
                                    ConversationStateStore conversationStateStore,
                                    UserMaxSeqPersistenceWriter persistenceWriter,
                                    GroupFanoutJobStore fanoutJobStore,
                                    @Value("${cheeseim.delivery.group-fanout.member-page-size:200}")
                                    int memberPageSize,
                                    @Value("${cheeseim.delivery.group-fanout.job-lease-seconds:60}")
                                    long jobLeaseSeconds) {
        this.objectMapper = objectMapper;
        this.groupMembershipFacade = groupMembershipFacade;
        this.planner = planner;
        this.messageProducer = messageProducer;
        this.conversationStateStore = conversationStateStore;
        this.persistenceWriter = persistenceWriter;
        this.fanoutJobStore = fanoutJobStore;
        this.memberPageSize = Math.min(2_000, Math.max(planner.batchSize(), memberPageSize));
        this.jobLeaseMillis = Math.min(60L, Math.max(10L, jobLeaseSeconds)) * 1_000L;
    }

    @QueueListener(topic = TopicNames.GROUP_FANOUT, group = "postmaster-group-fanout")
    public void onMessage(byte[] payload) {
        try {
            handle(objectMapper.readValue(payload, GroupFanoutEvent.class));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Malformed group fanout event", exception);
        }
    }

    void handle(GroupFanoutEvent event) {
        List<Message> messages = event == null ? List.of() : event.getMessages();
        if (event == null || event.getJobId() == null || event.getJobId().isBlank()
                || event.getGroupId() == null || event.getGroupId().isBlank() || messages.isEmpty()) {
            throw new IllegalArgumentException("Group fanout identity and messages are required");
        }
        Message sample = messages.get(0);
        long maxSeq = messages.get(messages.size() - 1).getSeq();
        if (event.getMembershipVersion() <= 0L) {
            throw new IllegalStateException(
                    "Group membership version is required: jobId=" + event.getJobId());
        }
        GroupMemberPage firstPage = groupMembershipFacade.loadGroupMembersPage(
                event.getGroupId(),
                event.getMembershipVersion(),
                Long.MIN_VALUE,
                "",
                "",
                memberPageSize);
        if (firstPage == null || !firstPage.isHasMore()) {
            processPage(event, messages, sample, maxSeq, firstPage);
            return;
        }
        String ownerToken = UUID.randomUUID().toString();
        GroupFanoutJobStore.Claim claim =
                claimWithWait(event.getJobId(), event.getMembershipVersion(), ownerToken);
        if (claim.status() == GroupFanoutJobStore.ClaimStatus.COMPLETED) {
            return;
        }
        if (claim.status() != GroupFanoutJobStore.ClaimStatus.ACQUIRED) {
            throw new IllegalStateException("Group fanout job lease is busy: " + event.getJobId());
        }
        if (claim.membershipVersion() <= 0L) {
            throw new IllegalStateException(
                    "Group fanout job has no persisted membership version: " + event.getJobId());
        }
        try {
            long afterJoinedVersion = claim.joinedVersion();
            String afterUserId = claim.userId();
            String afterEpochId = claim.epochId();
            GroupMemberPage prefetchedPage =
                    claim.membershipVersion() == event.getMembershipVersion()
                            && afterJoinedVersion == Long.MIN_VALUE
                            && nullSafe(afterUserId).isEmpty()
                            && nullSafe(afterEpochId).isEmpty()
                            ? firstPage
                            : null;
            while (true) {
                GroupMemberPage page = prefetchedPage;
                prefetchedPage = null;
                if (page == null) {
                    page = groupMembershipFacade.loadGroupMembersPage(
                            event.getGroupId(),
                            claim.membershipVersion(),
                            afterJoinedVersion,
                            afterUserId,
                            afterEpochId,
                            memberPageSize);
                }
                List<String> members = page == null ? List.of() : page.getUserIds();
                if (members.isEmpty()) {
                    if (page != null && page.isHasMore()) {
                        throw new IllegalStateException("Group member page cannot be empty when hasMore=true");
                    }
                    requireCompleted(event.getJobId(), ownerToken, claim.generation());
                    return;
                }
                processPage(event, messages, sample, maxSeq, page);
                if (!page.isHasMore()) {
                    requireCompleted(event.getJobId(), ownerToken, claim.generation());
                    return;
                }
                if (!cursorAdvanced(
                        afterJoinedVersion,
                        afterUserId,
                        afterEpochId,
                        page.getNextJoinedVersion(),
                        page.getNextUserId(),
                        page.getNextEpochId())) {
                    throw new IllegalStateException("Group member cursor did not advance");
                }
                boolean checkpointed = fanoutJobStore.checkpoint(
                        event.getJobId(),
                        ownerToken,
                        claim.generation(),
                        page.getNextJoinedVersion(),
                        page.getNextUserId(),
                        page.getNextEpochId(),
                        System.currentTimeMillis() + jobLeaseMillis);
                if (!checkpointed) {
                    throw new IllegalStateException(
                            "Group fanout job lost lease before checkpoint: " + event.getJobId());
                }
                afterJoinedVersion = page.getNextJoinedVersion();
                afterUserId = page.getNextUserId();
                afterEpochId = page.getNextEpochId();
            }
        } catch (RuntimeException exception) {
            fanoutJobStore.release(event.getJobId(), ownerToken, claim.generation());
            throw exception;
        }
    }

    private void processPage(GroupFanoutEvent event,
                             List<Message> messages,
                             Message sample,
                             long maxSeq,
                             GroupMemberPage page) {
        List<String> members = page == null ? List.of() : page.getUserIds();
        if (members.isEmpty()) {
            if (page != null && page.isHasMore()) {
                throw new IllegalStateException("Group member page cannot be empty when hasMore=true");
            }
            return;
        }
        if (event.isCreateConversation()) {
            conversationService.createGroupChatConversations(
                    event.getGroupId(), event.getConversationId(), members);
        }
        for (List<String> batch : planner.partition(members)) {
            fanoutBatch(event, messages, sample, maxSeq, batch);
        }
    }

    private GroupFanoutJobStore.Claim claimWithWait(String jobId,
                                                    long membershipVersion,
                                                    String ownerToken) {
        long waitDeadline = System.currentTimeMillis() + jobLeaseMillis;
        while (true) {
            long now = System.currentTimeMillis();
            GroupFanoutJobStore.Claim claim =
                    fanoutJobStore.claim(
                            jobId, membershipVersion, ownerToken, now, jobLeaseMillis);
            if (claim.status() != GroupFanoutJobStore.ClaimStatus.BUSY) {
                return claim;
            }
            long remaining = Math.min(waitDeadline, claim.leaseUntil()) - now;
            if (remaining <= 0L) {
                return claim;
            }
            try {
                Thread.sleep(Math.min(500L, remaining));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for fanout job lease", exception);
            }
        }
    }

    private void requireCompleted(String jobId, String ownerToken, long generation) {
        if (!fanoutJobStore.complete(jobId, ownerToken, generation, System.currentTimeMillis())) {
            throw new IllegalStateException("Group fanout job lost lease before completion: " + jobId);
        }
    }

    private void fanoutBatch(GroupFanoutEvent event,
                             List<Message> messages,
                             Message sample,
                             long maxSeq,
                             List<String> batch) {
            List<KeyedMessage<String>> targets = new ArrayList<>(batch.size());
            for (String memberId : batch) {
                targets.add(new KeyedMessage<>(planner.deliveryKey(event.getGroupId(), memberId), memberId));
            }
            messageProducer.publishForTargets(messages, targets);
            for (String memberId : batch) {
                conversationStateStore.advanceUserMaxSeq(
                        memberId, event.getConversationId(), maxSeq, !memberId.equals(sample.getSenderId()));
                persistenceWriter.enqueue(memberId, event.getConversationId(), maxSeq);
            }
    }

    private boolean cursorAdvanced(long previousVersion,
                                   String previousUserId,
                                   String previousEpochId,
                                   long nextVersion,
                                   String nextUserId,
                                   String nextEpochId) {
        if (nextVersion != previousVersion) {
            return nextVersion > previousVersion;
        }
        int userComparison = nullSafe(nextUserId).compareTo(nullSafe(previousUserId));
        if (userComparison != 0) {
            return userComparison > 0;
        }
        return nullSafe(nextEpochId).compareTo(nullSafe(previousEpochId)) > 0;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
