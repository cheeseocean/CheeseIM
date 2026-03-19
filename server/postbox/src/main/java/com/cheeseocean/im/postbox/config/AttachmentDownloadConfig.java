package com.cheeseocean.im.postbox.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AttachmentDownloadProperties.class)
public class AttachmentDownloadConfig {
}
