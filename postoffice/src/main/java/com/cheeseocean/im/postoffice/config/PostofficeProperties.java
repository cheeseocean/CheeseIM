package com.cheeseocean.im.postoffice.config;


import com.cheeseocean.im.common.config.YamlPropertySourceFactory;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:postoffice.yml", factory = YamlPropertySourceFactory.class)
@Data
public class PostofficeProperties {

    @Value("${postoffice.port}")
    private int port;

    @Value("${postoffice.ws-port}")
    private int wsPort;
}
