package com.cheeseocean.im.common.core.util;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class IdGenerator {

    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private IdGenerator() {
    }

    public static String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateMsgId() {
        return "msg_" + System.currentTimeMillis() + "_" + SEQUENCE.incrementAndGet();
    }

    public static String generateOperationId() {
        return "op_" + System.currentTimeMillis() + "_" + SEQUENCE.incrementAndGet();
    }

    public static long generateSeq() {
        return SEQUENCE.incrementAndGet();
    }
}
