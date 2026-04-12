package com.cheeseocean.im.postmaster.service;

import com.cheeseocean.im.common.api.dto.message.ConversationLastMessageSummary;
import com.cheeseocean.im.common.api.message.ConversationLastMessageQueryService;
import com.cheeseocean.im.common.core.store.conversation.ConversationStateStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@DubboService
public class ConversationLastMessageQueryServiceImpl implements ConversationLastMessageQueryService {

    private final ConversationStateStore conversationStateStore;
    private final ObjectMapper objectMapper;

    public ConversationLastMessageQueryServiceImpl(ConversationStateStore conversationStateStore,
                                                   ObjectMapper objectMapper) {
        this.conversationStateStore = conversationStateStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, ConversationLastMessageSummary> getLatestMessages(List<String> conversationIds) {
        LinkedHashSet<String> dedupedIds = new LinkedHashSet<>();
        if (conversationIds != null) {
            for (String conversationId : conversationIds) {
                if (conversationId != null && !conversationId.isBlank()) {
                    dedupedIds.add(conversationId);
                }
            }
        }
        if (dedupedIds.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, String> rawSummaries = conversationStateStore.getLastMessageSummaries(new ArrayList<>(dedupedIds));
        Map<String, ConversationLastMessageSummary> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawSummaries.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            try {
                result.put(entry.getKey(), objectMapper.readValue(
                        entry.getValue(), ConversationLastMessageSummary.class));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to deserialize conversation last message", e);
            }
        }
        return result;
    }
}
