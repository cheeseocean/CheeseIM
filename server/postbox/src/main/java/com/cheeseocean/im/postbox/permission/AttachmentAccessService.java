package com.cheeseocean.im.postbox.permission;

import com.cheeseocean.im.common.model.auth.PermissionCheckRequest;
import com.cheeseocean.im.common.model.auth.PermissionCheckResult;
import com.cheeseocean.im.postbox.entity.MessageDocument;
import com.cheeseocean.im.postbox.repository.MessageDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AttachmentAccessService {

    private final MessageDocumentRepository messageDocumentRepository;
    private final ConversationPermissionService conversationPermissionService;
    private final ObjectMapper objectMapper;

    public AttachmentAccessService(MessageDocumentRepository messageDocumentRepository,
                                   ConversationPermissionService conversationPermissionService,
                                   ObjectMapper objectMapper) {
        this.messageDocumentRepository = messageDocumentRepository;
        this.conversationPermissionService = conversationPermissionService;
        this.objectMapper = objectMapper;
    }

    public PermissionCheckResult checkAttachmentRead(String tenantId, String userId, String attachmentId) {
        MessageDocument message = findAttachmentMessage(attachmentId);
        if (message == null) {
            return PermissionCheckResult.deny("ATTACHMENT_NOT_FOUND", "attachment not found");
        }

        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setConversationId(message.getConversationId());
        request.setAction("READ");
        return conversationPermissionService.check(request);
    }

    public AttachmentDescriptor authorizeAttachment(String tenantId, String userId, String attachmentId) {
        PermissionCheckResult result = checkAttachmentRead(tenantId, userId, attachmentId);
        if (!result.isAllowed()) {
            throw new IllegalStateException(result.getMessage());
        }

        AttachmentDescriptor descriptor = resolveAttachment(attachmentId);
        if (descriptor == null) {
            throw new IllegalStateException("attachment metadata invalid");
        }
        return descriptor;
    }

    public AttachmentDescriptor resolveAttachment(String attachmentId) {
        MessageDocument message = findAttachmentMessage(attachmentId);
        AttachmentDescriptor descriptor = parseAttachedInfo(message == null ? null : message.getAttachedInfo());
        if (descriptor == null) {
            return null;
        }
        if (descriptor.getAttachmentId() == null || descriptor.getAttachmentId().isBlank()) {
            descriptor.setAttachmentId(attachmentId);
        }
        return descriptor;
    }

    private MessageDocument findAttachmentMessage(String attachmentId) {
        List<MessageDocument> candidates = messageDocumentRepository.findByAttachedInfoContaining(attachmentId);
        return candidates.stream()
                .filter(message -> {
                    AttachmentDescriptor descriptor = parseAttachedInfo(message.getAttachedInfo());
                    return descriptor != null && attachmentId.equals(descriptor.getAttachmentId());
                })
                .findFirst()
                .orElse(null);
    }

    private AttachmentDescriptor parseAttachedInfo(String attachedInfo) {
        if (attachedInfo == null || attachedInfo.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(attachedInfo);
            AttachmentDescriptor descriptor = new AttachmentDescriptor();
            descriptor.setAttachmentId(text(root, "attachmentId", "attachmentID", "fileId", "fileID"));
            descriptor.setStorageKey(text(root, "storageKey", "objectKey", "ossKey", "cosKey"));
            descriptor.setDownloadUrl(text(root, "downloadUrl", "url", "fileUrl", "fileURL"));
            return descriptor;
        } catch (Exception e) {
            return null;
        }
    }

    private String text(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node != null && !node.isNull()) {
                String value = node.asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
