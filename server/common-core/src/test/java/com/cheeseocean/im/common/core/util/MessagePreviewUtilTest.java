package com.cheeseocean.im.common.core.util;

import com.cheeseocean.im.common.core.constants.MessageConstants;
import com.cheeseocean.im.common.core.enums.MessagePreviewType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessagePreviewUtilTest {

    @Test
    void shouldResolveSpecialMessageTypePreview() {
        assertEquals("[已读回执]", MessagePreviewUtil.resolvePreview(MessageConstants.CONTENT_TYPE_READ_RECEIPT, "raw", Map.of()));
        assertEquals("你撤回了一条消息", MessagePreviewUtil.resolvePreview(MessageConstants.CONTENT_TYPE_REVOKE_NOTIFY, "raw", Map.of()));
        assertEquals("系统通知", MessagePreviewUtil.resolvePreview(MessageConstants.CONTENT_TYPE_SYSTEM_NOTIFY, "raw", Map.of()));
        assertEquals("安全提醒", MessagePreviewUtil.resolvePreview(MessageConstants.CONTENT_TYPE_FORCE_LOGOUT, "raw", Map.of()));
        assertNull(MessagePreviewUtil.resolvePreview(MessageConstants.CONTENT_TYPE_TYPING, "typing", Map.of()));
        assertEquals(MessagePreviewType.READ_RECEIPT, MessagePreviewUtil.resolvePreviewType(MessageConstants.CONTENT_TYPE_READ_RECEIPT, false));
        assertEquals(MessagePreviewType.REVOKE, MessagePreviewUtil.resolvePreviewType(MessageConstants.CONTENT_TYPE_REVOKE_NOTIFY, false));
        assertEquals(MessagePreviewType.SYSTEM, MessagePreviewUtil.resolvePreviewType(MessageConstants.CONTENT_TYPE_SYSTEM_NOTIFY, true));
        assertEquals(MessagePreviewType.SECURITY, MessagePreviewUtil.resolvePreviewType(MessageConstants.CONTENT_TYPE_FORCE_LOGOUT, true));
        assertEquals(MessagePreviewType.HIDDEN, MessagePreviewUtil.resolvePreviewType(MessageConstants.CONTENT_TYPE_TYPING, false));
    }

    @Test
    void shouldFallbackToContentOrAttachmentHint() {
        assertEquals("hello", MessagePreviewUtil.resolvePreview(101, "hello", Map.of()));
        assertEquals("Attachment", MessagePreviewUtil.resolvePreview(101, "", Map.of("attachedInfo", "1")));
        assertEquals(MessagePreviewType.TEXT, MessagePreviewUtil.resolvePreviewType(101, false));
    }
}
