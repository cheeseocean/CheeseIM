package com.cheeseocean.im.common.core.store.sequence;

public record SequenceRange(long startInclusive, long endInclusive) {

    public SequenceRange {
        if (endInclusive < startInclusive) {
            throw new IllegalArgumentException("endInclusive must be greater than or equal to startInclusive");
        }
    }

    public long size() {
        return endInclusive - startInclusive + 1L;
    }
}
