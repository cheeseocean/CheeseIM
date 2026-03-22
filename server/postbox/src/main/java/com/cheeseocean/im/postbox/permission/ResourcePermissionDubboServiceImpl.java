package com.cheeseocean.im.postbox.permission;

import com.cheeseocean.im.common.api.permission.ResourcePermissionDubboService;
import com.cheeseocean.im.common.core.auth.PermissionCheckResult;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class ResourcePermissionDubboServiceImpl implements ResourcePermissionDubboService {

    private final HistoryAccessService historyAccessService;
    private final AttachmentAccessService attachmentAccessService;

    public ResourcePermissionDubboServiceImpl(HistoryAccessService historyAccessService,
                                              AttachmentAccessService attachmentAccessService) {
        this.historyAccessService = historyAccessService;
        this.attachmentAccessService = attachmentAccessService;
    }

    @Override
    public PermissionCheckResult checkMessageRead(String tenantId, String userId, String messageId) {
        return historyAccessService.checkMessageRead(tenantId, userId, messageId);
    }

    @Override
    public PermissionCheckResult checkAttachmentRead(String tenantId, String userId, String attachmentId) {
        return attachmentAccessService.checkAttachmentRead(tenantId, userId, attachmentId);
    }
}
