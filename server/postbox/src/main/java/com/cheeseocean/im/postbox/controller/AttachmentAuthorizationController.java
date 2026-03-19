package com.cheeseocean.im.postbox.controller;

import com.cheeseocean.im.common.api.permission.ResourcePermissionDubboService;
import com.cheeseocean.im.common.model.auth.SessionPrincipal;
import com.cheeseocean.im.postbox.api.AttachmentDownloadAuthorizationResponse;
import com.cheeseocean.im.postbox.auth.AccessTokenSessionResolver;
import com.cheeseocean.im.postbox.permission.AttachmentAccessService;
import com.cheeseocean.im.postbox.permission.AttachmentDescriptor;
import com.cheeseocean.im.postbox.service.AttachmentDownloadTokenService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/im/attachments")
public class AttachmentAuthorizationController {

    private final AccessTokenSessionResolver accessTokenSessionResolver;
    private final AttachmentAccessService attachmentAccessService;
    private final AttachmentDownloadTokenService attachmentDownloadTokenService;

    public AttachmentAuthorizationController(AccessTokenSessionResolver accessTokenSessionResolver,
                                             AttachmentAccessService attachmentAccessService,
                                             AttachmentDownloadTokenService attachmentDownloadTokenService) {
        this.accessTokenSessionResolver = accessTokenSessionResolver;
        this.attachmentAccessService = attachmentAccessService;
        this.attachmentDownloadTokenService = attachmentDownloadTokenService;
    }

    @GetMapping("/{attachmentId}/download-url")
    public AttachmentDownloadAuthorizationResponse downloadUrl(@RequestHeader("Authorization") String authorization,
                                                               @PathVariable String attachmentId) {
        SessionPrincipal session = accessTokenSessionResolver.resolve(authorization);
        AttachmentDescriptor descriptor = attachmentAccessService.authorizeAttachment(
                session.getTenantId(), session.getUserId(), attachmentId);
        String token = attachmentDownloadTokenService.issueToken(attachmentId);
        AttachmentDownloadAuthorizationResponse response = new AttachmentDownloadAuthorizationResponse();
        response.setAttachmentId(descriptor.getAttachmentId());
        response.setDownloadUrl(attachmentDownloadTokenService.buildDownloadUrl(attachmentId, token));
        response.setStorageKey(descriptor.getStorageKey());
        response.setDownloadToken(token);
        response.setExpireAt(attachmentDownloadTokenService.resolveExpireAt());
        return response;
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Void> download(@PathVariable String attachmentId,
                                         @RequestParam("token") String token) {
        if (!attachmentDownloadTokenService.isValid(attachmentId, token)) {
            throw new IllegalStateException("attachment download token invalid");
        }
        AttachmentDescriptor descriptor = attachmentAccessService.resolveAttachment(attachmentId);
        if (descriptor == null || descriptor.getDownloadUrl() == null || descriptor.getDownloadUrl().isBlank()) {
            throw new IllegalStateException("attachment download unavailable");
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(descriptor.getDownloadUrl()).toString())
                .build();
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleForbidden(IllegalStateException e) {
        return Map.of("code", 40304, "message", e.getMessage());
    }
}
