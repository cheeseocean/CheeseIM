package com.cheeseocean.im.common.core.util;

public final class BlockIndexUtil {

    public static final int BLOCK_SIZE = 100;

    private BlockIndexUtil() {
    }

    public static long blockNo(long seq) {
        return (seq - 1) / BLOCK_SIZE;
    }

    public static int index(long seq) {
        return (int) ((seq - 1) % BLOCK_SIZE);
    }

    public static String docId(String conversationId, long seq) {
        return conversationId + ":" + blockNo(seq);
    }
}
