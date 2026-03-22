package com.cheeseocean.im.common.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockIndexUtilTest {

    @Test
    void blockIndexMapsSeqToBlockAndSlot() {
        assertEquals(0L, BlockIndexUtil.blockNo(1));
        assertEquals(0, BlockIndexUtil.index(1));
        assertEquals(1L, BlockIndexUtil.blockNo(101));
        assertEquals(0, BlockIndexUtil.index(101));
    }
}
