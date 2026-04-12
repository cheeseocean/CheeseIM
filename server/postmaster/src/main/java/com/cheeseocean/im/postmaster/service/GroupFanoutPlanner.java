package com.cheeseocean.im.postmaster.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GroupFanoutPlanner {

    private final int batchSize;

    public GroupFanoutPlanner(@Value("${cheeseim.delivery.group-fanout.batch-size:500}") int batchSize) {
        this.batchSize = batchSize;
    }

    public List<List<String>> partition(List<String> memberIds) {
        List<List<String>> batches = new ArrayList<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return batches;
        }
        for (int start = 0; start < memberIds.size(); start += batchSize) {
            int end = Math.min(start + batchSize, memberIds.size());
            batches.add(new ArrayList<>(memberIds.subList(start, end)));
        }
        return batches;
    }
}
