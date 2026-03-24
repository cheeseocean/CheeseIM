package com.cheeseocean.im.common.core.store.sequence;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationSequenceAllocatorTest {

    @Test
    void shouldAllocateSequencesFromReservedRanges() {
        RecordingSequenceStore store = new RecordingSequenceStore();
        ConversationSequenceAllocator allocator = new ConversationSequenceAllocator(store, 2);

        assertEquals(1L, allocator.nextSeq("c1"));
        assertEquals(2L, allocator.nextSeq("c1"));
        assertEquals(3L, allocator.nextSeq("c1"));
        assertEquals(4L, allocator.nextSeq("c1"));
        assertEquals(2, store.reserveCalls.get("c1").intValue());
    }

    private static final class RecordingSequenceStore implements SequenceStore {

        private final Map<String, Long> highest = new HashMap<>();
        private final Map<String, Integer> reserveCalls = new HashMap<>();

        @Override
        public SequenceRange reserve(String conversationId, int size) {
            reserveCalls.merge(conversationId, 1, Integer::sum);
            long start = highest.getOrDefault(conversationId, 0L) + 1L;
            long end = start + size - 1L;
            highest.put(conversationId, end);
            return new SequenceRange(start, end);
        }
    }
}
