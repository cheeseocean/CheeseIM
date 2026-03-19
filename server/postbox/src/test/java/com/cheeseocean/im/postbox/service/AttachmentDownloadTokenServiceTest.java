package com.cheeseocean.im.postbox.service;

import com.cheeseocean.im.postbox.config.AttachmentDownloadProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentDownloadTokenServiceTest {

    @Test
    void issuedTokenShouldValidateForMatchingAttachment() {
        AttachmentDownloadProperties properties = new AttachmentDownloadProperties();
        properties.setTokenSecret("test-secret");
        properties.setTokenTtlSeconds(300);

        AttachmentDownloadTokenService service = new AttachmentDownloadTokenService(properties);
        String token = service.issueToken("att_123");

        assertTrue(service.isValid("att_123", token));
        assertFalse(service.isValid("att_456", token));
    }

    @Test
    void buildDownloadUrlShouldUseConfiguredBaseUrlWhenPresent() {
        AttachmentDownloadProperties properties = new AttachmentDownloadProperties();
        properties.setTokenSecret("test-secret");
        properties.setPublicBaseUrl("https://im.example.com");

        AttachmentDownloadTokenService service = new AttachmentDownloadTokenService(properties);

        String url = service.buildDownloadUrl("att_123", "token_xxx");

        assertTrue(url.startsWith("https://im.example.com/api/im/attachments/att_123/download?token="));
    }
}
