package com.cheeseocean.im.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class CommonHttpConfig {

    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        ((SimpleClientHttpRequestFactory) factory).setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        ((SimpleClientHttpRequestFactory) factory).setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }
}
