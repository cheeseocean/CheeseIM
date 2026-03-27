package com.cheeseocean.im.common.core.store.sequence;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ConversationSequenceAllocator {

    private final SequenceStore sequenceStore;
    private final int reserveSize;
    private final Map<String, ActiveRange> activeRanges = new HashMap<>();

    public ConversationSequenceAllocator(SequenceStore sequenceStore) {
        this(sequenceStore, 100);
    }

    public ConversationSequenceAllocator(SequenceStore sequenceStore, int reserveSize) {
        this.sequenceStore = Objects.requireNonNull(sequenceStore, "sequenceStore");
        if (reserveSize <= 0) {
            throw new IllegalArgumentException("reserveSize must be positive");
        }
        this.reserveSize = reserveSize;
    }

    public synchronized long nextSeq(String conversationId) {
        Objects.requireNonNull(conversationId, "conversationId");
        return nextSeqLocked(conversationId);
    }

    public long nextSequence(String conversationId) {
        return nextSeq(conversationId);
    }

    public synchronized SequenceRange allocateRange(String conversationId, int count) {
        Objects.requireNonNull(conversationId, "conversationId");
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        long first = nextSeqLocked(conversationId);
        for (int i = 1; i < count; i++) {
            nextSeqLocked(conversationId);
        }
        return new SequenceRange(first, first + count - 1);
    }

    private long nextSeqLocked(String conversationId) {
        ActiveRange activeRange = activeRanges.get(conversationId);
        if (activeRange == null || !activeRange.hasRemaining()) {
            SequenceRange reserved = sequenceStore.reserve(conversationId, reserveSize);
            activeRange = new ActiveRange(reserved.startInclusive(), reserved.endInclusive());
            activeRanges.put(conversationId, activeRange);
        }
        return activeRange.next();
    }

    private static final class ActiveRange {

        private long nextValue;
        private final long endInclusive;

        private ActiveRange(long startInclusive, long endInclusive) {
            this.nextValue = startInclusive;
            this.endInclusive = endInclusive;
        }

        private boolean hasRemaining() {
            return nextValue <= endInclusive;
        }

        private long next() {
            return nextValue++;
        }
    }
}
