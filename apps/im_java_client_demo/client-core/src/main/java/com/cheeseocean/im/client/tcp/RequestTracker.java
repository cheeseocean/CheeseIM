package com.cheeseocean.im.client.tcp;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RequestTracker {

    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    public void remember(String operationId, String requestType) {
        pendingRequests.put(operationId, new PendingRequest(operationId, requestType, Instant.now().toEpochMilli()));
    }

    public Optional<PendingRequest> find(String operationId) {
        return Optional.ofNullable(pendingRequests.get(operationId));
    }

    public Optional<PendingRequest> resolve(String operationId) {
        return Optional.ofNullable(pendingRequests.remove(operationId));
    }

    public record PendingRequest(String operationId, String requestType, long createdAtMillis) {
    }
}
