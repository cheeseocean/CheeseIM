package com.cheeseocean.im.common.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IdGeneratorTest {

    @Test
    void shouldGenerateExpectedIdPrefixes() {
        assertTrue(IdGenerator.generateUUID().length() >= 32);
        assertTrue(IdGenerator.generateMsgId().startsWith("msg_"));
        assertTrue(IdGenerator.generateOperationId().startsWith("op_"));
        assertTrue(IdGenerator.generateSeq() > 0);
    }
}
