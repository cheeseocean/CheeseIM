package com.cheeseocean.im.postbox.history;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageBlockDocTest {

    @Test
    void getMessagesShouldExpandSparseLegacyMessageMap() {
        MessageBlockDoc doc = new MessageBlockDoc();
        Map<String, MessageSlot> stored = new LinkedHashMap<>();
        stored.put("0", slot(1L, "first"));
        stored.put("2", slot(3L, "third"));
        doc.setMessageMap(stored);

        assertEquals(3, doc.getMessages().size());
        assertEquals("first", doc.getMessages().get(0).getContent());
        assertNull(doc.getMessages().get(1));
        assertEquals("third", doc.getMessages().get(2).getContent());
    }

    private static MessageSlot slot(long seq, String content) {
        MessageSlot slot = new MessageSlot();
        slot.setSeq(seq);
        slot.setContent(content);
        return slot;
    }
}
