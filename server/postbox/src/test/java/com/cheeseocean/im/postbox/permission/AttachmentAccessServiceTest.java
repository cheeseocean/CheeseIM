package com.cheeseocean.im.postbox.permission;

import com.cheeseocean.im.common.model.auth.PermissionCheckResult;
import com.cheeseocean.im.postbox.service.BlockMessageQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentAccessServiceTest {

    @Test
    void authorizeAttachmentShouldResolveDescriptorFromBlockHistoryCandidate() {
        BlockMessageQueryService blockMessageQueryService = mock(BlockMessageQueryService.class);
        ConversationPermissionService permissionService = mock(ConversationPermissionService.class);
        when(blockMessageQueryService.findAttachmentCandidates("att-1", 20))
                .thenReturn(List.of(new BlockMessageQueryService.AttachmentMessageCandidate(
                        "single:userA:userB",
                        "msg-1",
                        "{\"attachmentId\":\"att-1\",\"storageKey\":\"oss/a\",\"downloadUrl\":\"https://cdn.example.com/a\"}")));
        when(permissionService.check(any())).thenReturn(PermissionCheckResult.allow());

        AttachmentAccessService service = new AttachmentAccessService(blockMessageQueryService, permissionService, new ObjectMapper());

        AttachmentDescriptor descriptor = service.authorizeAttachment("tenant-1", "userB", "att-1");

        assertNotNull(descriptor);
        assertEquals("att-1", descriptor.getAttachmentId());
        assertEquals("oss/a", descriptor.getStorageKey());
    }
}
