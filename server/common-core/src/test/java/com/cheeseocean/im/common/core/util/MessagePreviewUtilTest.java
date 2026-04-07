package com.cheeseocean.im.common.core.util;

import com.cheeseocean.im.common.api.enums.ContentType;
import com.cheeseocean.im.common.api.enums.MessagePreviewType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessagePreviewUtilTest {

    @Test
    void shouldResolveSpecialMessageTypePreview() {
        assertEquals("[已读回执]", MessagePreviewUtil.resolvePreview(ContentType.READ_RECEIPT.getCode(), "raw", Map.of()));
        assertEquals("你撤回了一条消息", MessagePreviewUtil.resolvePreview(ContentType.REVOKE_NOTIFY.getCode(), "raw", Map.of()));
        assertEquals("系统通知", MessagePreviewUtil.resolvePreview(ContentType.SYSTEM_NOTIFY.getCode(), "raw", Map.of()));
        assertEquals("安全提醒", MessagePreviewUtil.resolvePreview(ContentType.FORCE_LOGOUT.getCode(), "raw", Map.of()));
        assertNull(MessagePreviewUtil.resolvePreview(ContentType.TYPING.getCode(), "typing", Map.of()));
        assertEquals(MessagePreviewType.READ_RECEIPT, MessagePreviewUtil.resolvePreviewType(ContentType.READ_RECEIPT.getCode(), false));
        assertEquals(MessagePreviewType.REVOKE, MessagePreviewUtil.resolvePreviewType(ContentType.REVOKE_NOTIFY.getCode(), false));
        assertEquals(MessagePreviewType.SYSTEM, MessagePreviewUtil.resolvePreviewType(ContentType.SYSTEM_NOTIFY.getCode(), true));
        assertEquals(MessagePreviewType.SECURITY, MessagePreviewUtil.resolvePreviewType(ContentType.FORCE_LOGOUT.getCode(), true));
        assertEquals(MessagePreviewType.HIDDEN, MessagePreviewUtil.resolvePreviewType(ContentType.TYPING.getCode(), false));
    }

    @Test
    void shouldFallbackToContentOrAttachmentHint() {
        assertEquals("hello", MessagePreviewUtil.resolvePreview(ContentType.TEXT.getCode(), "hello", Map.of()));
        assertEquals("Attachment", MessagePreviewUtil.resolvePreview(ContentType.TEXT.getCode(), "", Map.of("attachedInfo", "1")));
        assertEquals(MessagePreviewType.TEXT, MessagePreviewUtil.resolvePreviewType(ContentType.TEXT.getCode(), false));
    }
}
