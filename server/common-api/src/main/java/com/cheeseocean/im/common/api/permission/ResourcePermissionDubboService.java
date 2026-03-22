package com.cheeseocean.im.common.api.permission;

import com.cheeseocean.im.common.core.auth.PermissionCheckResult;

public interface ResourcePermissionDubboService {

    PermissionCheckResult checkMessageRead(String tenantId, String userId, String messageId);

    PermissionCheckResult checkAttachmentRead(String tenantId, String userId, String attachmentId);
}
