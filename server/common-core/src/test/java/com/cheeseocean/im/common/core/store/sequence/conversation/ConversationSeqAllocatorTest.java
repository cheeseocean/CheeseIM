package com.cheeseocean.im.common.core.store.sequence.conversation;

import com.cheeseocean.im.common.core.business.repository.ConversationRangeRepository;
import com.cheeseocean.im.common.core.store.sequence.SequenceRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationSeqAllocatorTest {

    @Test
    void allocateShouldInitializeCacheFromMongoOnMiss() {
        ConversationSeqCacheStore cacheStore = mock(ConversationSeqCacheStore.class);
        ConversationRangeRepository rangeRepository = mock(ConversationRangeRepository.class);
        when(cacheStore.allocate("s:u100:u200", 2, 100L))
                .thenReturn(new ConversationSeqCacheResult(ConversationSeqRangeState.MISS, 0L, 0L, "owner-1", 100L));
        when(rangeRepository.allocate("s:u100:u200", 52L)).thenReturn(0L);

        ConversationSeqAllocator allocator = new ConversationSeqAllocator(cacheStore, rangeRepository, 50, 100, 10);

        SequenceRange range = allocator.allocate("s:u100:u200", 2, 100L);

        assertEquals(1L, range.startInclusive());
        assertEquals(2L, range.endInclusive());
        verify(cacheStore).install("s:u100:u200", "owner-1", 2L, 52L, 100L);
    }

    @Test
    void allocateShouldContinueFromCacheWhenMongoMatchesLastSeq() {
        ConversationSeqCacheStore cacheStore = mock(ConversationSeqCacheStore.class);
        ConversationRangeRepository rangeRepository = mock(ConversationRangeRepository.class);
        when(cacheStore.allocate("g:g100", 2, 100L))
                .thenReturn(new ConversationSeqCacheResult(ConversationSeqRangeState.EXHAUSTED, 10L, 10L, "owner-2", 100L));
        when(rangeRepository.allocate("g:g100", 102L)).thenReturn(10L);

        ConversationSeqAllocator allocator = new ConversationSeqAllocator(cacheStore, rangeRepository, 50, 100, 10);

        SequenceRange range = allocator.allocate("g:g100", 2, 100L);

        assertEquals(11L, range.startInclusive());
        assertEquals(12L, range.endInclusive());
        verify(cacheStore).install("g:g100", "owner-2", 12L, 112L, 100L);
    }

    @Test
    void allocateShouldRewriteFromMongoWhenCacheAndMongoDiffer() {
        ConversationSeqCacheStore cacheStore = mock(ConversationSeqCacheStore.class);
        ConversationRangeRepository rangeRepository = mock(ConversationRangeRepository.class);
        when(cacheStore.allocate("g:g200", 1, 100L))
                .thenReturn(new ConversationSeqCacheResult(ConversationSeqRangeState.EXHAUSTED, 10L, 10L, "owner-3", 100L));
        when(rangeRepository.allocate("g:g200", 101L)).thenReturn(20L);

        ConversationSeqAllocator allocator = new ConversationSeqAllocator(cacheStore, rangeRepository, 50, 100, 10);

        SequenceRange range = allocator.allocate("g:g200", 1, 100L);

        assertEquals(21L, range.startInclusive());
        assertEquals(21L, range.endInclusive());
        verify(cacheStore).install("g:g200", "owner-3", 21L, 121L, 100L);
    }

    @Test
    void getMaxSeqShouldLoadFromMongoOnCacheMiss() {
        ConversationSeqCacheStore cacheStore = mock(ConversationSeqCacheStore.class);
        ConversationRangeRepository rangeRepository = mock(ConversationRangeRepository.class);
        when(cacheStore.allocate("s:u100:u300", 0, 100L))
                .thenReturn(new ConversationSeqCacheResult(ConversationSeqRangeState.MISS, 0L, 0L, "owner-4", 100L));
        when(rangeRepository.getMaxSeq("s:u100:u300")).thenReturn(18L);

        ConversationSeqAllocator allocator = new ConversationSeqAllocator(cacheStore, rangeRepository, 50, 100, 10);

        long maxSeq = allocator.getMaxSeq("s:u100:u300", 100L);

        assertEquals(18L, maxSeq);
        verify(cacheStore).install("s:u100:u300", "owner-4", 18L, 18L, 100L);
    }
}
