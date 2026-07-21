package com.cheeseocean.im.postmaster.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupFanoutPlannerTest {

    @Test
    void hotGroupShouldBeSplitIntoFanoutBatches() {
        GroupFanoutPlanner planner = new GroupFanoutPlanner(500, 10, 524288);
        List<List<String>> batches = planner.partition(java.util.stream.IntStream.rangeClosed(1, 1201)
                .mapToObj(i -> "user-" + i)
                .toList());

        assertEquals(3, batches.size());
        assertEquals(500, batches.get(0).size());
        assertEquals(500, batches.get(1).size());
        assertEquals(201, batches.get(2).size());
    }

    @Test
    void groupMessageShouldCreateSingleBatchWhenTargetsFit() {
        GroupFanoutPlanner planner = new GroupFanoutPlanner(500, 10, 524288);
        List<List<String>> batches = planner.partition(List.of("userB", "userC"));

        assertEquals(1, batches.size());
        assertEquals(List.of("userB", "userC"), batches.get(0));
    }
}
