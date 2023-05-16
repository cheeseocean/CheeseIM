package com.cheeseocean.im.gateway.config;

import com.cheeseocean.im.message.api.MessageService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BaseConfig {

    @Bean
    @DubboReference
    public ReferenceBean<MessageService> chatService() {
        return new ReferenceBean<>();
    }

}
