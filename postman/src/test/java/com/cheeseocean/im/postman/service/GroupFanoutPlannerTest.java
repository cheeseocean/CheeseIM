package com.cheeseocean.im.postman.service;

import com.cheeseocean.im.common.dto.DeliveryCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupFanoutPlannerTest {

    @Test
    void hotGroupShouldBeSplitIntoFanoutBatches() {
        GroupFanoutPlanner planner = new GroupFanoutPlanner(500);
        DeliveryCommand command = DeliveryCommand.builder()
                .clientMsgId("c-g-1")
                .conversationId("group:g-1")
                .senderId("userA")
                .sessionType(2)
                .targetUserIds(java.util.stream.IntStream.rangeClosed(1, 1201)
                        .mapToObj(i -> "user-" + i)
                        .toList())
                .build();

        GroupFanoutPlanner.FanoutPlan plan = planner.plan(command, command.getTargetUserIds());

        assertEquals(3, plan.getBatches().size());
        assertEquals(500, plan.getBatches().get(0).getReceiverIds().size());
        assertEquals(500, plan.getBatches().get(1).getReceiverIds().size());
        assertEquals(201, plan.getBatches().get(2).getReceiverIds().size());
    }

    @Test
    void groupMessageShouldCreateOnePlanForAllTargets() {
        GroupFanoutPlanner planner = new GroupFanoutPlanner(500);
        DeliveryCommand command = DeliveryCommand.builder()
                .clientMsgId("c-g-2")
                .conversationId("group:g-2")
                .senderId("userA")
                .sessionType(2)
                .targetUserIds(List.of("userB", "userC"))
                .build();

        GroupFanoutPlanner.FanoutPlan plan = planner.plan(command, command.getTargetUserIds());

        assertEquals("group:g-2", plan.getConversationId());
        assertEquals(1, plan.getBatches().size());
        assertEquals(List.of("userB", "userC"), plan.getBatches().get(0).getReceiverIds());
    }
}
