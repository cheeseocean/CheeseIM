package com.cheeseocean.im.client.tcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestTrackerTest {

    @Test
    void trackerShouldRememberOutboundOperationMetadata() {
        RequestTracker tracker = new RequestTracker();

        tracker.remember("op-1", "auth");

        assertTrue(tracker.find("op-1").isPresent());
        assertEquals("auth", tracker.find("op-1").orElseThrow().requestType());
    }

    @Test
    void trackerShouldResolveAndRemoveOperationOnResponse() {
        RequestTracker tracker = new RequestTracker();
        tracker.remember("op-2", "send");

        RequestTracker.PendingRequest pending = tracker.resolve("op-2").orElseThrow();

        assertEquals("send", pending.requestType());
        assertFalse(tracker.find("op-2").isPresent());
    }
}
