package com.cheeseocean.im.common.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentTypeTest {

    @Test
    void fromCodeShouldResolveReadReceiptContentType() {
        assertEquals(com.cheeseocean.im.common.api.enums.ContentType.READ_RECEIPT, com.cheeseocean.im.common.api.enums.ContentType.fromCode(com.cheeseocean.im.common.api.enums.ContentType.READ_RECEIPT.getCode()));
    }

    @Test
    void fromCodeShouldRejectUnknownContentType() {
        assertThrows(IllegalArgumentException.class, () -> com.cheeseocean.im.common.api.enums.ContentType.fromCode(999999));
    }
}
