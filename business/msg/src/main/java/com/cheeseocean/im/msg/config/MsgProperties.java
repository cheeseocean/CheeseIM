package com.cheeseocean.im.msg.config;

import com.cheeseocean.im.common.config.YamlPropertySourceFactory;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.Map;

/**
 * @author xxxcrel
 * Created on 2023/4/17
 */
@PropertySource(value = "classpath:message.yml", factory = YamlPropertySourceFactory.class)
@Configuration
@Data
public class MsgProperties {

    @Value("${kafka}")
    private Map<String, Object> kafka;

}
