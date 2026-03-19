package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.dto.DeliveryCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GroupFanoutPlanner {

    private final int batchSize;

    public GroupFanoutPlanner(@Value("${cheese.im.delivery.group-fanout.batch-size:500}") int batchSize) {
        this.batchSize = batchSize;
    }

    public FanoutPlan plan(DeliveryCommand command, List<String> memberIds) {
        List<FanoutBatch> batches = new ArrayList<>();
        for (int start = 0; start < memberIds.size(); start += batchSize) {
            int end = Math.min(start + batchSize, memberIds.size());
            batches.add(new FanoutBatch(memberIds.subList(start, end)));
        }
        return new FanoutPlan(command.getConversationId(), batches);
    }

    public static final class FanoutPlan {
        private final String conversationId;
        private final List<FanoutBatch> batches;

        public FanoutPlan(String conversationId, List<FanoutBatch> batches) {
            this.conversationId = conversationId;
            this.batches = List.copyOf(batches);
        }

        public String getConversationId() {
            return conversationId;
        }

        public List<FanoutBatch> getBatches() {
            return batches;
        }
    }

    public static final class FanoutBatch {
        private final List<String> receiverIds;

        public FanoutBatch(List<String> receiverIds) {
            this.receiverIds = List.copyOf(receiverIds);
        }

        public List<String> getReceiverIds() {
            return receiverIds;
        }
    }
}
